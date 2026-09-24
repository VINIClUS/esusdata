package esusdata.result;

import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.model.PublicationAuthorization;
import esusdata.result.model.PublicationOutcome;
import esusdata.result.model.PublicationRefusedException;
import esusdata.result.model.PublicationRequest;
import esusdata.run.extract.ExtractionFilePaths;
import esusdata.run.job.JobRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The one short transaction that turns a sealed staging row into a published result (§1.9.5):
 * verify ownership → insert the extraction manifest if new → insert {@code results} → mark
 * staging {@code PUBLISHED} → move the job to {@code SUCCEEDED}. Every step is a CAS against the
 * exact process/generation/state this run believes it owns; any mismatch rolls back the whole
 * transaction and nothing becomes visible (ENG-30).
 *
 * <p>The extract's existence/integrity is checked once, <em>before</em> this transaction opens —
 * SQLite and files never share one transaction (§1.9.5). A missing/corrupted extract does not
 * block publication of the already-computed result (the evidence is already durable in {@code
 * result_staging}/{@code evidence}), but it must not read as quietly fine either — §1.9.5:
 * "resultado apontando para arquivo perdido fica indisponível/limitado, nunca silenciosamente
 * reproduzível." So it both downgrades {@code reproducibility_level} <em>and</em> adds an
 * explicit entry to {@code limitations_json} — the one field nothing in this store lets a reader
 * miss, unlike a dimension column a caller has to know to inspect.
 */
public final class PublicationService {

    private static final RowMapper<StagingSnapshot> STAGING_MAPPER = (rs, rowNum) -> new StagingSnapshot(
            rs.getString("staging_id"),
            rs.getString("state"),
            rs.getString("job_id"),
            rs.getLong("execution_generation"),
            rs.getString("process_instance_id"),
            rs.getString("indicator_pack"),
            rs.getString("rule_version"),
            rs.getString("municipality_ibge"),
            rs.getString("reference_period"),
            rs.getString("status"),
            rs.getString("value_text"),
            rs.getString("numerator_text"),
            rs.getString("denominator_text"),
            rs.getString("denominator_kind"),
            rs.getString("classification"),
            rs.getString("data_cutoff"),
            rs.getString("extraction_id"),
            rs.getString("adapter_version"),
            rs.getString("calculation_policy_version"),
            rs.getString("limitations_json"),
            rs.getString("input_fingerprint"),
            rs.getString("evidence_grain"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final JobRepository jobRepository;
    private final ExtractionManifestRepository extractionManifestRepository;
    private final ReproducibilityCheck reproducibilityCheck;
    private final Path extractsBaseDir;
    private final PublicationAuthorization publicationAuthorization;
    private final ObjectMapper mapper = new ObjectMapper();

    public PublicationService(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            JobRepository jobRepository,
            ExtractionManifestRepository extractionManifestRepository,
            ReproducibilityCheck reproducibilityCheck,
            Path extractsBaseDir,
            PublicationAuthorization publicationAuthorization) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.jobRepository = jobRepository;
        this.extractionManifestRepository = extractionManifestRepository;
        this.reproducibilityCheck = reproducibilityCheck;
        this.extractsBaseDir = extractsBaseDir;
        this.publicationAuthorization = publicationAuthorization;
    }

    public PublicationOutcome publish(PublicationRequest request) {
        ReproducibilityCheck.Outcome fileCheck =
                reproducibilityCheck.verify(request.extractionManifest().extractionId());
        String reproducibilityLevel = fileCheck.reproducible() ? "REPRODUCIBLE" : "NOT_REPRODUCIBLE";

        return transactionTemplate.execute(status -> {
            StagingSnapshot staging = requireOwnedSealedStaging(request);

            // §1.9.4 L365: revalidated against CURRENT grants, not the session that made the
            // original request — a revocation since acquisition began must still block this
            // publish, without erasing any earlier published history.
            publicationAuthorization.requireStillAuthorized(
                    request.authorizedPrincipal(), request.authorizedMunicipalityIbge());

            if (!extractionManifestRepository.existsById(staging.extractionId())) {
                extractionManifestRepository.save(
                        request.extractionManifest(),
                        ExtractionFilePaths.dataFile(extractsBaseDir, staging.extractionId()));
            }

            String resultId = "res-" + UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO results (result_id, job_id, run_id, staging_id, source_id,
                        indicator_pack, rule_version, municipality_ibge, reference_period, status,
                        value_text, numerator_text, denominator_text, denominator_kind,
                        classification, data_cutoff, extraction_id, adapter_version,
                        calculation_policy_version, limitations_json, input_fingerprint,
                        result_nature, validation_status, completeness_status, consistency_level,
                        reproducibility_level, canonical_schema_version, evidence_grain,
                        app_build, published_at)
                    VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?)
                    """,
                    resultId,
                    staging.jobId(),
                    request.runId(),
                    staging.stagingId(),
                    request.sourceId(),
                    staging.indicatorPack(),
                    staging.ruleVersion(),
                    staging.municipalityIbge(),
                    staging.referencePeriod(),
                    staging.status(),
                    staging.valueText(),
                    staging.numeratorText(),
                    staging.denominatorText(),
                    staging.denominatorKind(),
                    staging.classification(),
                    staging.dataCutoff(),
                    staging.extractionId(),
                    staging.adapterVersion(),
                    staging.calculationPolicyVersion(),
                    augmentLimitations(staging.limitationsJson(), fileCheck),
                    staging.inputFingerprint(),
                    request.resultNature(),
                    request.validationStatus(),
                    request.extractionManifest().completenessStatus(),
                    request.extractionManifest().consistencyLevel(),
                    reproducibilityLevel,
                    request.extractionManifest().canonicalSchemaVersion(),
                    staging.evidenceGrain(),
                    request.appBuild(),
                    request.publishedAt().toString());

            int stagingUpdated = jdbc.update(
                    "update result_staging set state = 'PUBLISHED' where staging_id = ? and state = 'SEALED'",
                    staging.stagingId());
            if (stagingUpdated != 1) {
                status.setRollbackOnly();
                throw new PublicationRefusedException(
                        "staging " + staging.stagingId() + " changed state during publication");
            }

            boolean jobUpdated = jobRepository.markSucceededAndRecordAttempt(
                    request.jobId(),
                    request.processInstanceId(),
                    request.executionGeneration(),
                    staging.stagingId(),
                    request.publishedAt());
            if (!jobUpdated) {
                status.setRollbackOnly();
                throw new PublicationRefusedException(
                        "job " + request.jobId() + " is no longer STAGED under this process/"
                                + "generation — refusing to publish (cancel/publish race, ENG-23)");
            }

            return new PublicationOutcome(resultId, reproducibilityLevel);
        });
    }

    /**
     * Adds a visible, unmissable limitation when the referenced extract is gone or corrupted —
     * {@code reproducibility_level} alone is a dimension a caller has to know to inspect;
     * {@code limitations_json} is not (§1.11: every result screen shows limitations).
     */
    private String augmentLimitations(String limitationsJson, ReproducibilityCheck.Outcome fileCheck) {
        if (fileCheck.reproducible()) {
            return limitationsJson;
        }
        String[] existing = mapper.readValue(limitationsJson, String[].class);
        List<String> limitations = new ArrayList<>(java.util.Arrays.asList(existing));
        limitations.add("extraction_source_unavailable: o extrato de origem não pôde ser "
                + "verificado (existência/integridade) no momento da publicação"
                + (fileCheck.reason() == null ? "" : " (" + fileCheck.reason() + ")") + ".");
        return mapper.writeValueAsString(limitations);
    }

    private StagingSnapshot requireOwnedSealedStaging(PublicationRequest request) {
        List<StagingSnapshot> rows =
                jdbc.query("select * from result_staging where staging_id = ?", STAGING_MAPPER, request.stagingId());
        StagingSnapshot staging = rows.stream()
                .findFirst()
                .orElseThrow(() -> new PublicationRefusedException("no staging row for " + request.stagingId()));
        if (!"SEALED".equals(staging.state())) {
            throw new PublicationRefusedException(
                    "staging " + staging.stagingId() + " is not SEALED (state=" + staging.state() + ")");
        }
        if (!staging.jobId().equals(request.jobId())
                || staging.executionGeneration() != request.executionGeneration()
                || !Optional.ofNullable(staging.processInstanceId())
                        .equals(Optional.ofNullable(request.processInstanceId()))) {
            throw new PublicationRefusedException(
                    "staging " + staging.stagingId() + " ownership does not match this publication request");
        }
        return staging;
    }

    private record StagingSnapshot(
            String stagingId,
            String state,
            String jobId,
            long executionGeneration,
            String processInstanceId,
            String indicatorPack,
            String ruleVersion,
            String municipalityIbge,
            String referencePeriod,
            String status,
            String valueText,
            String numeratorText,
            String denominatorText,
            String denominatorKind,
            String classification,
            String dataCutoff,
            String extractionId,
            String adapterVersion,
            String calculationPolicyVersion,
            String limitationsJson,
            String inputFingerprint,
            String evidenceGrain) {}
}
