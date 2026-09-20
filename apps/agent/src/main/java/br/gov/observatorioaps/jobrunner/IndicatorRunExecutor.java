package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.extractionstore.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.ExtractReader;
import br.gov.observatorioaps.extractionstore.ExtractionManifest;
import br.gov.observatorioaps.indicatorengine.IndicatorResult;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import br.gov.observatorioaps.resultstore.EvidenceEntry;
import br.gov.observatorioaps.resultstore.InputFingerprint;
import br.gov.observatorioaps.resultstore.PublicationOutcome;
import br.gov.observatorioaps.resultstore.PublicationRefusedException;
import br.gov.observatorioaps.resultstore.PublicationRequest;
import br.gov.observatorioaps.resultstore.PublicationService;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import br.gov.observatorioaps.resultstore.StagingRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Orchestrates one job run over an already-finalized immutable extract: read → compute (C1) →
 * stage → publish. Today this class is C1-specific (the only indicator pack wired into the
 * pilot, §4.5.1) — a second pack would need either a small strategy seam here or its own
 * executor; premature to build before there is a second rule to generalize from.
 *
 * <p>C1's release gates are not complete (§4.4 Portões A/B/D/E {@code BLOCKED} — Q01 not
 * retrieved), so every result computed here is {@code status=BLOCKED} with exact counts, never a
 * fabricated value. {@link C1Rule#computeEvidenceOnly} is deliberately not used on this path —
 * it exists only to prove reproducibility in tests (ENG-19), not to publish a methodologically
 * unreleased indicator as if it had passed the gates.
 *
 * <p>{@code LIVE_READ_ONLY} acquisition (opening a fresh PEC connection from a queued job) is out
 * of scope for this phase. This executor only replays an already-finalized
 * {@code IMMUTABLE_EXTRACT}; {@link JobWorker} rejects a job whose {@code extractionId} is absent
 * rather than silently pretending to support live acquisition.
 */
public final class IndicatorRunExecutor {

    private static final String EVIDENCE_GRAIN = "SOURCE_EVENT";
    // §1.2/§1.10.1: LOCAL_ESTIMATE | OFFICIAL_IMPORTED | SIMULATION — a calculation from a local
    // immutable PEC extract is never OFFICIAL_IMPORTED (that label is reserved for results
    // imported from an official source with its own provenance, never earned by numeric
    // resemblance) and never SIMULATION.
    private static final String RESULT_NATURE = "LOCAL_ESTIMATE";
    // C1's release gates (Portões A/B/D/E, §4.4) are not complete — see class javadoc.
    private static final String VALIDATION_STATUS = "NOT_VALIDATED";

    private final Path extractsBaseDir;
    private final ExtractReader extractReader = new ExtractReader();
    private final JobRepository jobRepository;
    private final ResultStagingArea stagingArea;
    private final PublicationService publicationService;
    private final String appBuild;
    private final Clock clock;

    public IndicatorRunExecutor(
            Path extractsBaseDir,
            JobRepository jobRepository,
            ResultStagingArea stagingArea,
            PublicationService publicationService,
            String appBuild,
            Clock clock) {
        this.extractsBaseDir = extractsBaseDir;
        this.jobRepository = jobRepository;
        this.stagingArea = stagingArea;
        this.publicationService = publicationService;
        this.appBuild = appBuild;
        this.clock = clock;
    }

    public record RunContext(
            String jobId, String runId, String sourceId, long executionGeneration,
            String processInstanceId, String extractionId, String municipalityIbge,
            String referencePeriod, String indicatorPack, String ruleVersion) {
    }

    public record RunOutcome(String stagingId, String resultId, IndicatorResult result) {
    }

    public RunOutcome runFromExtract(RunContext context, CancellationToken cancellation) throws IOException {
        // This executor only ever computes C1 — reject anything else before doing any I/O rather
        // than silently publishing a C1 result under a different pack/version's name.
        if (!C1Rule.INDICATOR_PACK.equals(context.indicatorPack())
                || !C1Rule.RULE_VERSION.equals(context.ruleVersion())) {
            throw new IllegalArgumentException(
                    "job requests " + context.indicatorPack() + "@" + context.ruleVersion()
                            + " but this executor only computes "
                            + C1Rule.INDICATOR_PACK + "@" + C1Rule.RULE_VERSION);
        }

        ExtractionManifest manifest = extractReader.readManifest(extractsBaseDir, context.extractionId());
        YearMonth requestedPeriod = YearMonth.parse(context.referencePeriod());
        String expectedPeriodStart = requestedPeriod.atDay(1).toString();
        String expectedPeriodEndExclusive = requestedPeriod.plusMonths(1).atDay(1).toString();
        if (!manifest.sourceId().equals(context.sourceId())
                || !manifest.municipalityIbge().equals(context.municipalityIbge())
                || !manifest.periodStart().equals(expectedPeriodStart)
                || !manifest.periodEndExclusive().equals(expectedPeriodEndExclusive)) {
            // The extract file matches the requested extractionId but its actual scope (source,
            // municipality, or period) does not match what the job asked for — all FKs stay
            // individually valid, so nothing else would catch this. This matters even for an
            // extract with zero matching records: C1Rule's per-record checks never run on an
            // empty extract, so a scope mismatch would otherwise publish a plausible-looking
            // zero-count result under the wrong municipality/period with wrong provenance,
            // silently — exactly what §1.10.1 forbids.
            throw new IllegalStateException(
                    "extract " + context.extractionId() + " covers source " + manifest.sourceId()
                            + "/municipality " + manifest.municipalityIbge() + "/period ["
                            + manifest.periodStart() + ", " + manifest.periodEndExclusive()
                            + ") but job " + context.jobId() + " requested source " + context.sourceId()
                            + "/municipality " + context.municipalityIbge() + "/period "
                            + context.referencePeriod());
        }
        List<CanonicalEncounter> encounters = extractReader.readEncounters(extractsBaseDir, manifest);
        cancellation.checkCancelled();

        String dataCutoff = requestedPeriod.atEndOfMonth().toString();
        IndicatorResult result = C1Rule.compute(
                encounters, context.municipalityIbge(), context.referencePeriod(), dataCutoff);
        cancellation.checkCancelled();

        String stagingId = "stg-" + UUID.randomUUID();
        String inputFingerprint = computeInputFingerprint(manifest, result);

        stagingArea.open(new StagingRequest(
                stagingId, context.jobId(), context.executionGeneration(), context.processInstanceId(),
                clock.instant(), C1Rule.INDICATOR_PACK, result, manifest.extractionId(),
                manifest.adapterVersion(), EVIDENCE_GRAIN, inputFingerprint));
        stagingArea.writeEvidence(stagingId, toEvidence(encounters, result));
        cancellation.checkCancelled();
        stagingArea.seal(stagingId);

        boolean staged = jobRepository.markStaged(
                context.jobId(), context.processInstanceId(), context.executionGeneration(), stagingId);
        if (!staged) {
            stagingArea.neutralize(stagingId);
            throw new PublicationRefusedException(
                    "job " + context.jobId() + " ownership changed before staging could be recorded");
        }

        PublicationOutcome outcome = publicationService.publish(new PublicationRequest(
                context.jobId(), context.runId(), stagingId, context.sourceId(),
                context.executionGeneration(), context.processInstanceId(), manifest,
                RESULT_NATURE, VALIDATION_STATUS, appBuild, clock.instant()));

        return new RunOutcome(stagingId, outcome.resultId(), result);
    }

    private String computeInputFingerprint(ExtractionManifest manifest, IndicatorResult result) {
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("source_id", manifest.sourceId());
        fields.put("municipality_ibge", manifest.municipalityIbge());
        fields.put("extraction_id", manifest.extractionId());
        fields.put("extraction_checksum", manifest.checksum());
        fields.put("acquisition_plan", "IMMUTABLE_EXTRACT");
        fields.put("indicator_pack", C1Rule.INDICATOR_PACK);
        fields.put("rule_version", result.ruleVersion());
        fields.put("reference_period", result.referencePeriod());
        fields.put("data_cutoff", result.dataCutoff());
        fields.put("calculation_policy_version", result.calculationPolicyVersion());
        fields.put("adapter_version", manifest.adapterVersion());
        return InputFingerprint.compute(fields);
    }

    private List<EvidenceEntry> toEvidence(List<CanonicalEncounter> encounters, IndicatorResult result) {
        List<EvidenceEntry> entries = new ArrayList<>(encounters.size());
        for (CanonicalEncounter e : encounters) {
            String decision = switch (e.modality()) {
                case PROGRAMADO -> "IN_NUMERATOR";
                case ESPONTANEO -> "DENOMINATOR_ONLY";
                case UNMAPPED -> "EXCLUDED_UNMAPPED";
            };
            entries.add(new EvidenceEntry(
                    e.sourceRef().entityType(), e.sourceRef().recordId(), e.careDate(),
                    e.modality().name(), e.cnes(), e.ine(), e.cbo(), decision, result.ruleVersion()));
        }
        return entries;
    }
}
