package br.gov.observatorioaps.execution.application;

import br.gov.observatorioaps.indicators.domain.CanonicalEncounter;
import br.gov.observatorioaps.execution.domain.extract.ExtractStore;
import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;
import br.gov.observatorioaps.access.application.GrantRevalidator;
import br.gov.observatorioaps.access.domain.Permission;
import br.gov.observatorioaps.indicators.domain.IndicatorResult;
import br.gov.observatorioaps.indicators.packs.c1.C1Rule;
import br.gov.observatorioaps.results.domain.EvidenceEntry;
import br.gov.observatorioaps.results.domain.InputFingerprint;
import br.gov.observatorioaps.results.domain.PublicationOutcome;
import br.gov.observatorioaps.results.domain.PublicationRefusedException;
import br.gov.observatorioaps.results.domain.PublicationRequest;
import br.gov.observatorioaps.results.application.PublicationService;
import br.gov.observatorioaps.results.domain.ResultStagingArea;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionCommand;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionListener;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionPort;
import br.gov.observatorioaps.execution.domain.acquisition.CancellationSignal;
import br.gov.observatorioaps.execution.domain.acquisition.PecConnectionProperties;
import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
import br.gov.observatorioaps.sources.domain.SourceRecord;
import br.gov.observatorioaps.sources.domain.SourceRepository;
import br.gov.observatorioaps.results.domain.StagingRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
/**
 * Orchestrates one job run: read (from an already-finalized extract, or from a fresh PEC
 * acquisition via {@link AcquisitionPort}) → compute (C1) → stage → publish. Today this class is
 * C1-specific (the only indicator pack wired into the pilot, §4.5.1) — a second pack would need
 * either a small strategy seam here or its own executor; premature to build before there is a
 * second rule to generalize from.
 *
 * <p>C1's release gates are not complete (§4.4 Portões A/B/D/E {@code BLOCKED} — Q01 not
 * retrieved), so every result computed here is {@code status=BLOCKED} with exact counts, never a
 * fabricated value. {@link C1Rule#computeEvidenceOnly} is deliberately not used on this path — it
 * exists only to prove reproducibility in tests (ENG-19), not to publish a methodologically
 * unreleased indicator as if it had passed the gates.
 *
 * <p>§1.9.4 L365 is checked at the start of BOTH {@link #runFromExtract} and {@link #runLive} —
 * against the principal's CURRENT grants, not whatever authorized the original HTTP request.
 * Closing a browser tab or letting the session expire does not cancel an already-authorized job;
 * only an actual grant/account change does.
 *
 * <p>Reading (from disk or from a live PEC acquisition) is behind ports ({@link ExtractStore},
 * {@link AcquisitionPort}) — this class no longer imports JDBC, the PEC driver, or any other
 * module's {@code infrastructure} package (ADR 0009).
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
    private static final String SOURCE_ZONE_ID = "America/Sao_Paulo";

    private final ExtractStore extractStore;
    private final JobRepository jobRepository;
    private final ResultStagingArea stagingArea;
    private final PublicationService publicationService;
    private final String appBuild;
    private final Clock clock;
    private final GrantRevalidator grantRevalidator;
    private final SourceRepository sourceRepository;
    private final AcquisitionPort acquisitionPort;
    private final AcquisitionGuard acquisitionGuard;
    private final Duration liveAcquisitionCooldownMargin;

    public IndicatorRunExecutor(
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea stagingArea,
            PublicationService publicationService,
            String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            AcquisitionPort acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin) {
        this.extractStore = extractStore;
        this.jobRepository = jobRepository;
        this.stagingArea = stagingArea;
        this.publicationService = publicationService;
        this.appBuild = appBuild;
        this.clock = clock;
        this.grantRevalidator = grantRevalidator;
        this.sourceRepository = sourceRepository;
        this.acquisitionPort = acquisitionPort;
        this.acquisitionGuard = acquisitionGuard;
        this.liveAcquisitionCooldownMargin = liveAcquisitionCooldownMargin;
    }

    public record RunContext(
            String jobId, String runId, String sourceId, long executionGeneration,
            String processInstanceId, String extractionId, String municipalityIbge,
            String referencePeriod, String indicatorPack, String ruleVersion,
            String idempotencyPrincipal) {
    }

    public record RunOutcome(String stagingId, String resultId, IndicatorResult result) {
    }

    public RunOutcome runFromExtract(RunContext context, CancellationSignal cancellation) throws IOException {
        requireC1(context);
        grantRevalidator.requireCurrentlyAuthorized(
                context.idempotencyPrincipal(), context.municipalityIbge(), Permission.RUN_INDICATOR);

        ExtractionManifest manifest = extractStore.readManifest(context.extractionId());
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
        List<CanonicalEncounter> encounters = extractStore.readEncounters(manifest);
        cancellation.checkCancelled();

        return computeStageAndPublish(context, manifest, encounters, cancellation);
    }

    /**
     * Acquires directly from the PEC through {@link AcquisitionPort}, then computes from the
     * finalized extract exactly like {@link #runFromExtract} — reading it back from disk rather
     * than keeping streamed rows in memory, so both paths share one "recompute from durable
     * evidence" code path (the same invariant ENG-19 proves for replay).
     */
    public RunOutcome runLive(RunContext context, CancellationSignal cancellation) throws IOException {
        requireC1(context);
        grantRevalidator.requireCurrentlyAuthorized(
                context.idempotencyPrincipal(), context.municipalityIbge(), Permission.RUN_INDICATOR);

        SourceRecord source = sourceRepository.findById(context.sourceId())
                .orElseThrow(() -> new IllegalStateException(
                        "source " + context.sourceId() + " is not registered"));
        if (!source.municipalityIbge().equals(context.municipalityIbge())) {
            throw new IllegalStateException(
                    "source " + source.id() + " is authorized for municipality "
                            + source.municipalityIbge() + " but job " + context.jobId()
                            + " requested municipality " + context.municipalityIbge());
        }
        acquisitionGuard.requireUnblocked(context.sourceId());

        PecConnectionProperties properties = new PecConnectionProperties(
                source.id(), source.host(), source.port(), source.databaseName(),
                source.dbUser(), source.secretRef(), source.municipalityIbge());
        PecSourceIdentity sourceIdentity = new PecSourceIdentity(
                source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
        ReadBudget budget = ReadBudget.initialEngineeringProposal();

        YearMonth requestedPeriod = YearMonth.parse(context.referencePeriod());
        LocalDate periodStart = requestedPeriod.atDay(1);
        LocalDate periodEndExclusive = requestedPeriod.plusMonths(1).atDay(1);
        String extractionId = "live-" + context.jobId() + "-g" + context.executionGeneration();

        AcquisitionCommand command = new AcquisitionCommand(
                properties, sourceIdentity, budget, extractionId, periodStart, periodEndExclusive,
                SOURCE_ZONE_ID);

        ExtractionManifest manifest = acquisitionPort.acquire(command, cancellation, new AcquisitionListener() {
            @Override
            public void onProgress() {
                jobRepository.markProgress(context.jobId(), context.processInstanceId(),
                        context.executionGeneration(), clock.instant());
            }

            @Override
            public void onUncertainOutcome(String reason) {
                // Block new LIVE_READ_ONLY acquisitions on this source for the same
                // timeout-derived margin JobRecovery applies to an abandoned RUNNING job, rather
                // than only guarding against a process restart (ENG-51).
                acquisitionGuard.block(context.sourceId(),
                        clock.instant().plus(liveAcquisitionCooldownMargin), reason);
            }
        });
        cancellation.checkCancelled();

        List<CanonicalEncounter> encounters = extractStore.readEncounters(manifest);
        return computeStageAndPublish(context, manifest, encounters, cancellation);
    }

    private void requireC1(RunContext context) {
        // This executor only ever computes C1 — reject anything else before doing any I/O rather
        // than silently publishing a C1 result under a different pack/version's name.
        if (!C1Rule.INDICATOR_PACK.equals(context.indicatorPack())
                || !C1Rule.RULE_VERSION.equals(context.ruleVersion())) {
            throw new IllegalArgumentException(
                    "job requests " + context.indicatorPack() + "@" + context.ruleVersion()
                            + " but this executor only computes "
                            + C1Rule.INDICATOR_PACK + "@" + C1Rule.RULE_VERSION);
        }
    }

    private RunOutcome computeStageAndPublish(
            RunContext context, ExtractionManifest manifest,
            List<CanonicalEncounter> encounters, CancellationSignal cancellation) {
        YearMonth requestedPeriod = YearMonth.parse(context.referencePeriod());
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
                RESULT_NATURE, VALIDATION_STATUS, appBuild, clock.instant(),
                context.idempotencyPrincipal(), context.municipalityIbge()));

        return new RunOutcome(stagingId, outcome.resultId(), result);
    }

    private String computeInputFingerprint(ExtractionManifest manifest, IndicatorResult result) {
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("source_id", manifest.sourceId());
        fields.put("municipality_ibge", manifest.municipalityIbge());
        fields.put("extraction_id", manifest.extractionId());
        fields.put("extraction_checksum", manifest.checksum());
        fields.put("acquisition_plan", manifest.extractionId().startsWith("live-")
                ? "LIVE_READ_ONLY" : "IMMUTABLE_EXTRACT");
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
