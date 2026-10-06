package esusdata.run.worker;

import esusdata.auth.GrantRevalidator;
import esusdata.auth.model.Permission;
import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.GateChecks;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.result.PublicationService;
import esusdata.result.model.EvidenceEntry;
import esusdata.result.model.InputFingerprint;
import esusdata.result.model.PublicationOutcome;
import esusdata.result.model.PublicationRefusedException;
import esusdata.result.model.PublicationRequest;
import esusdata.result.model.ResultStagingArea;
import esusdata.result.model.StagingRequest;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionListener;
import esusdata.run.acquisition.CancellationSignal;
import esusdata.run.extract.ExtractStore;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestPart;
import esusdata.run.job.JobRepository;
import esusdata.source.SourceRepository;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Orchestrates one job run: read (from an already-finalized extract, or from a fresh PEC
 * acquisition via {@link Acquisition}) → compute → stage → publish, for whichever compiled rule the
 * job names (ADR 0030): {@link RuleLookup} — {@link IndicatorRuleRegistry#require} in production —
 * turns an unknown pack or rule version into {@code INVALID_REQUEST} before any I/O.
 *
 * <p>C1 keeps its v1 command, read budget, encounters and per-encounter evidence. It also reads the
 * team states as a supplementary canonical v2 extract of the same run ({@link ReadPlan}, ADR 0033),
 * first, and its input fingerprint names both extracts; its result carries the per-team breakdown
 * {@code C1Pack} computes. Its Portão D has not passed, so every C1 result is {@code status=BLOCKED}
 * with exact counts, never a fabricated value.
 *
 * <p>The release gates (ADR 0032) are applied here and nowhere else, to every pack and to the
 * legacy C1 path alike: the rules return ungated outcomes, this class reads A and D from the
 * {@link ReleaseGateRegistry}, evaluates B (no blocking standing limitation) and C (every required
 * capability {@code VALIDATED} for the source), and stages the gate snapshot with the result — the
 * V12 {@code CHECK} refuses a {@code COMPUTED} result staged without one.
 *
 * <p>A canonical v2 pack (C2–C7) is checked for eligibility first: every capability it reads needs
 * a {@code VALIDATED} compatibility entry for the source's PEC version, read model and installation
 * role, or the run fails with {@code UNSUPPORTED_SOURCE} before the acquisition guard and before
 * any child process — definitive, with no cooldown. The acquisition then reads every part the rule
 * requires in one transaction ({@link ReadPlan}); the result is gated ({@link RuleOutcomes#gate}),
 * staged with its practices, teams and generic evidence, and published. Its input fingerprint also names every part read.
 *
 * <p>§1.9.4 L365 is checked at the start of BOTH {@link #runFromExtract} and {@link #runLive} —
 * against the principal's CURRENT grants, not whatever authorized the original HTTP request.
 * Closing a browser tab or letting the session expire does not cancel an already-authorized job;
 * only an actual grant/account change does.
 *
 * <p>Reading (from disk or from a live PEC acquisition) is behind ports ({@link ExtractStore},
 * {@link Acquisition}) — this class never imports JDBC, the PEC driver, or any other module's
 * {@code infrastructure} package (ADR 0009).
 */
public final class RunExecutor {

    /** C1's evidence grain since V2: one row per source event. */
    private static final String EVENT_EVIDENCE_GRAIN = "SOURCE_EVENT";

    /** C2–C7 (ADR 0030): one row per subject (person or episode) and practice, plus supporting events. */
    private static final String SUBJECT_EVIDENCE_GRAIN = "SUBJECT_PRACTICE";

    // §1.2/§1.10.1: LOCAL_ESTIMATE | OFFICIAL_IMPORTED | SIMULATION — a calculation from a local
    // immutable PEC extract is never OFFICIAL_IMPORTED (that label is reserved for results
    // imported from an official source with its own provenance, never earned by numeric
    // resemblance) and never SIMULATION.
    private static final String RESULT_NATURE = "LOCAL_ESTIMATE";
    // No pack has its release gates passed yet (ADR 0032) — see class javadoc.
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
    private final Acquisition acquisitionPort;
    private final AcquisitionGuard acquisitionGuard;
    private final Duration liveAcquisitionCooldownMargin;
    private final CapabilityEligibility eligibility;
    private final RuleLookup rules;
    private final ReleaseGateRegistry gateRegistry;
    private final CapabilityCatalog catalog = CapabilityCatalog.packaged();

    /** How a job's pack and rule version resolve to a compiled rule (ADR 0030). */
    @FunctionalInterface
    public interface RuleLookup {

        /**
         * The rule to run, or {@link IllegalArgumentException} for an unknown pack or version.
         */
        IndicatorRule require(String indicatorPack, String ruleVersion);
    }

    /** The production wiring: the release's rules and the packaged compatibility matrix. */
    public RunExecutor(
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea stagingArea,
            PublicationService publicationService,
            String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            Acquisition acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin) {
        this(
                extractStore,
                jobRepository,
                stagingArea,
                publicationService,
                appBuild,
                clock,
                grantRevalidator,
                sourceRepository,
                acquisitionPort,
                acquisitionGuard,
                liveAcquisitionCooldownMargin,
                new CapabilityEligibility(PecCompatibilityMatrix.fromClasspathResource()),
                IndicatorRuleRegistry::require);
    }

    public RunExecutor(
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea stagingArea,
            PublicationService publicationService,
            String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            Acquisition acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin,
            CapabilityEligibility eligibility,
            RuleLookup rules) {
        this(
                extractStore,
                jobRepository,
                stagingArea,
                publicationService,
                appBuild,
                clock,
                grantRevalidator,
                sourceRepository,
                acquisitionPort,
                acquisitionGuard,
                liveAcquisitionCooldownMargin,
                eligibility,
                rules,
                ReleaseGateRegistry.bundled());
    }

    /** As above, with the release-gate registry the results are gated by (ADR 0032). */
    public RunExecutor(
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea stagingArea,
            PublicationService publicationService,
            String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            Acquisition acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin,
            CapabilityEligibility eligibility,
            RuleLookup rules,
            ReleaseGateRegistry gateRegistry) {
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
        this.eligibility = eligibility;
        this.rules = rules;
        this.gateRegistry = gateRegistry;
    }

    public record RunContext(
            String jobId,
            String runId,
            String sourceId,
            long executionGeneration,
            String processInstanceId,
            String extractionId,
            String municipalityIbge,
            String referencePeriod,
            String indicatorPack,
            String ruleVersion,
            String idempotencyPrincipal) {}

    public record RunOutcome(String stagingId, String resultId, IndicatorResult result) {}

    public RunOutcome runFromExtract(RunContext context, CancellationSignal cancellation) throws IOException {
        IndicatorRule rule = rules.require(context.indicatorPack(), context.ruleVersion());
        ReadPlan plan = ReadPlan.of(rule, YearMonth.parse(context.referencePeriod()), catalog);
        grantRevalidator.requireCurrentlyAuthorized(
                context.idempotencyPrincipal(), context.municipalityIbge(), Permission.RUN_INDICATOR);

        ExtractionManifest manifest = extractStore.readManifest(context.extractionId());
        plan.requireCovers(manifest, context);
        ExtractionManifest supplement = plan.hasSupplement()
                ? extractStore.readManifest(ReadPlan.supplementExtractionId(manifest.extractionId()))
                : null;
        CanonicalDataset data = read(plan, manifest, supplement);
        cancellation.checkCancelled();

        PecSourceIdentity identity = sourceRepository
                .findById(manifest.sourceId())
                .flatMap(RunExecutor::identityOf)
                .orElse(null);
        return computeStageAndPublish(context, rule, plan, manifest, supplement, data, identity, cancellation);
    }

    /** The extract of the run: with its supplementary extract when the plan reads one (ADR 0033). */
    private CanonicalDataset read(ReadPlan plan, ExtractionManifest manifest, ExtractionManifest supplement)
            throws IOException {
        return supplement == null ? plan.read(extractStore, manifest) : plan.read(extractStore, manifest, supplement);
    }

    /**
     * Acquires directly from the PEC through {@link Acquisition}, then computes from the
     * finalized extract exactly like {@link #runFromExtract} — reading it back from disk rather
     * than keeping streamed rows in memory, so both paths share one "recompute from durable
     * evidence" code path (the same invariant ENG-19 proves for replay).
     */
    public RunOutcome runLive(RunContext context, CancellationSignal cancellation) throws IOException {
        IndicatorRule rule = rules.require(context.indicatorPack(), context.ruleVersion());
        ReadPlan plan = ReadPlan.of(rule, YearMonth.parse(context.referencePeriod()), catalog);
        grantRevalidator.requireCurrentlyAuthorized(
                context.idempotencyPrincipal(), context.municipalityIbge(), Permission.RUN_INDICATOR);

        SourceRecord source = sourceRepository
                .findById(context.sourceId())
                .orElseThrow(() -> new IllegalStateException("source " + context.sourceId() + " is not registered"));
        if (!source.municipalityIbge().equals(context.municipalityIbge())) {
            throw new IllegalStateException("source " + source.id() + " is authorized for municipality "
                    + source.municipalityIbge() + " but job " + context.jobId()
                    + " requested municipality " + context.municipalityIbge());
        }
        if (plan.isCanonicalV2()) {
            // ADR 0030: decided here, in Java, before the guard and before any child — a probe
            // that diverged inside the execution plane would block the whole source by cooldown.
            eligibility.require(
                    rule.descriptor().id(),
                    plan.capabilities(),
                    CapabilityEligibility.identityOf(
                                    source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole())
                            .orElse(null));
        } else if (plan.hasSupplement()) {
            // ADR 0033: the supplementary capability is checked as a v2 pack's are — before the
            // guard and before any child; the v1 capability keeps the probe it has always had
            eligibility.require(
                    rule.descriptor().id(),
                    plan.supplementCapabilities(),
                    CapabilityEligibility.identityOf(
                                    source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole())
                            .orElse(null));
        }
        acquisitionGuard.requireUnblocked(context.sourceId());

        PecConnectionProperties properties = new PecConnectionProperties(
                source.id(),
                source.host(),
                source.port(),
                source.databaseName(),
                source.dbUser(),
                source.secretRef(),
                source.municipalityIbge());
        PecSourceIdentity sourceIdentity = new PecSourceIdentity(
                source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
        String extractionId = "live-" + context.jobId() + "-g" + context.executionGeneration();
        AcquisitionCommand command = plan.command(properties, sourceIdentity, extractionId, SOURCE_ZONE_ID);

        AcquisitionListener listener = new AcquisitionListener() {
            @Override
            public void onProgress() {
                jobRepository.markProgress(
                        context.jobId(), context.processInstanceId(), context.executionGeneration(), clock.instant());
            }

            @Override
            public void onUncertainOutcome(String reason) {
                // Block new LIVE_READ_ONLY acquisitions on this source for the same
                // timeout-derived margin JobRecovery applies to an abandoned RUNNING job, rather
                // than only guarding against a process restart (ENG-51).
                acquisitionGuard.block(context.sourceId(), clock.instant().plus(liveAcquisitionCooldownMargin), reason);
            }
        };
        // The small transactional read first: it fails fast, before the large DW read (ADR 0033).
        ExtractionManifest supplement = null;
        if (plan.hasSupplement()) {
            supplement = acquisitionPort.acquire(
                    plan.supplementCommand(properties, sourceIdentity, extractionId, SOURCE_ZONE_ID),
                    cancellation,
                    listener);
            cancellation.checkCancelled();
        }
        ExtractionManifest manifest = acquisitionPort.acquire(command, cancellation, listener);
        cancellation.checkCancelled();

        if (plan.isCanonicalV2()) {
            plan.requireCovers(manifest, context);
        }
        CanonicalDataset data = read(plan, manifest, supplement);
        PecSourceIdentity identity = identityOf(source).orElse(null);
        return computeStageAndPublish(context, rule, plan, manifest, supplement, data, identity, cancellation);
    }

    private RunOutcome computeStageAndPublish(
            RunContext context,
            IndicatorRule rule,
            ReadPlan plan,
            ExtractionManifest manifest,
            ExtractionManifest supplement,
            CanonicalDataset data,
            PecSourceIdentity identity,
            CancellationSignal cancellation) {
        PackDescriptor descriptor = rule.descriptor();
        EvaluationContext evaluation =
                EvaluationContext.endOfMonth(context.municipalityIbge(), YearMonth.parse(context.referencePeriod()));
        GateStatus gates = gatesOf(descriptor, identity);
        RuleOutcome outcome = gate(plan, gates, RuleOutcomes.disclose(descriptor, rule.evaluate(data, evaluation)));
        IndicatorResult result = outcome.result();
        requireJobScope(descriptor, result, context);
        cancellation.checkCancelled();

        String stagingId = "stg-" + UUID.randomUUID();
        String inputFingerprint = computeInputFingerprint(manifest, supplement, descriptor.id(), result, plan);

        stagingArea.open(new StagingRequest(
                stagingId,
                context.jobId(),
                context.executionGeneration(),
                context.processInstanceId(),
                clock.instant(),
                descriptor.id(),
                result,
                manifest.extractionId(),
                manifest.adapterVersion(),
                plan.isCanonicalV2() ? SUBJECT_EVIDENCE_GRAIN : EVENT_EVIDENCE_GRAIN,
                inputFingerprint,
                outcome.teams(),
                ReleaseGateRegistry.snapshotJson(gates)));
        stagingArea.writeEvidence(stagingId, toEvidence(outcome.evidence(), result.ruleVersion()));
        cancellation.checkCancelled();
        stagingArea.seal(stagingId);

        boolean staged = jobRepository.markStaged(
                context.jobId(), context.processInstanceId(), context.executionGeneration(), stagingId);
        if (!staged) {
            stagingArea.neutralize(stagingId);
            throw new PublicationRefusedException(
                    "job " + context.jobId() + " ownership changed before staging could be recorded");
        }

        PublicationOutcome published = publicationService.publish(new PublicationRequest(
                context.jobId(),
                context.runId(),
                stagingId,
                context.sourceId(),
                context.executionGeneration(),
                context.processInstanceId(),
                manifest,
                RESULT_NATURE,
                VALIDATION_STATUS,
                appBuild,
                clock.instant(),
                context.idempotencyPrincipal(),
                context.municipalityIbge()));

        return new RunOutcome(stagingId, published.resultId(), result);
    }

    /** A source's declared identity, empty when it is incomplete: such a source validates nothing. */
    private static Optional<PecSourceIdentity> identityOf(SourceRecord source) {
        return CapabilityEligibility.identityOf(
                source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
    }

    /**
     * Where the gates stand for this run: A and D as the registry records them for the compiled rule
     * version, B from the pack's blocking limitations, C from the source's {@code VALIDATED}
     * capabilities. Evaluated on every run, so a result never carries a stale verdict.
     */
    private GateStatus gatesOf(PackDescriptor descriptor, PecSourceIdentity identity) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(SOURCE_ZONE_ID)));
        return gateRegistry
                .statusOf(descriptor)
                .withEvaluated(
                        GateChecks.calculationModel(descriptor, today),
                        GateChecks.adapter(eligibility.missing(descriptor.requiredCapabilities(), identity), today));
    }

    /**
     * The one place a rule's outcome meets the release gates (ADR 0032). C1's legacy path keeps its
     * v0.1.9 status for a result without a denominator: {@code BLOCKED} while a gate has not
     * passed, where C2-C7 have always passed {@code NO_DENOMINATOR} through. S2 revisits it.
     */
    private static RuleOutcome gate(ReadPlan plan, GateStatus gates, RuleOutcome ungated) {
        RuleOutcome gated = RuleOutcomes.gate(gates, ungated);
        return plan.isCanonicalV2() || gates.isComplete() ? gated : RuleOutcomes.blockEmptyDenominators(gated);
    }

    /**
     * The staged row takes its municipality, competência and rule version from the result itself, so
     * a rule answering for anything but the job is refused rather than published under it.
     */
    private static void requireJobScope(PackDescriptor descriptor, IndicatorResult result, RunContext context) {
        if (!context.municipalityIbge().equals(result.municipalityIbge())
                || !context.referencePeriod().equals(result.referencePeriod())
                || !descriptor.ruleVersion().equals(result.ruleVersion())
                || descriptor.valueKind() != result.valueKind()) {
            throw new IllegalStateException(descriptor.ruleVersion() + " answered for " + result.ruleVersion() + " "
                    + result.municipalityIbge() + " " + result.referencePeriod() + " (" + result.valueKind()
                    + ") but job " + context.jobId() + " asked for " + context.municipalityIbge() + " "
                    + context.referencePeriod());
        }
    }

    private static String computeInputFingerprint(
            ExtractionManifest manifest,
            ExtractionManifest supplement,
            String indicatorPack,
            IndicatorResult result,
            ReadPlan plan) {
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("source_id", manifest.sourceId());
        fields.put("municipality_ibge", manifest.municipalityIbge());
        fields.put("extraction_id", manifest.extractionId());
        fields.put("extraction_checksum", manifest.checksum());
        fields.put(
                "acquisition_plan",
                manifest.extractionId().startsWith("live-") ? "LIVE_READ_ONLY" : "IMMUTABLE_EXTRACT");
        fields.put("indicator_pack", indicatorPack);
        fields.put("rule_version", result.ruleVersion());
        fields.put("reference_period", result.referencePeriod());
        fields.put("data_cutoff", result.dataCutoff());
        fields.put("calculation_policy_version", result.calculationPolicyVersion());
        fields.put("adapter_version", manifest.adapterVersion());
        if (plan.isCanonicalV2()) {
            fields.put("canonical_schema_version", manifest.canonicalSchemaVersion());
            fields.put("parts", partsFingerprint(manifest.parts()));
        }
        if (supplement != null) {
            // ADR 0033: the input is both extracts, so the fingerprint names the second and every part read
            fields.put("supplement_extraction_id", supplement.extractionId());
            fields.put("supplement_extraction_checksum", supplement.checksum());
            fields.put("supplement_parts", partsFingerprint(supplement.parts()));
        }
        return InputFingerprint.compute(fields);
    }

    /** One line per part, in part order: what was read, how, over which window, and how much. */
    private static String partsFingerprint(List<ManifestPart> parts) {
        List<ManifestPart> ordered = new ArrayList<>(parts);
        ordered.sort(Comparator.comparingInt(ManifestPart::index));
        List<String> lines = new ArrayList<>(ordered.size());
        for (ManifestPart part : ordered) {
            lines.add(part.index() + ":" + part.capability() + "@" + part.adapterVersion() + ":" + part.recordKind()
                    + ":" + part.queryChecksum() + ":[" + part.periodStart() + "," + part.periodEndExclusive() + "):"
                    + part.paramsChecksum() + ":" + part.rowCount());
        }
        return String.join(";", lines);
    }

    /**
     * Evidence rows as the rule emitted them, in order (the {@code seq} of each row). C1's {@code
     * EVENT} rows map to exactly the columns V2 has always stored.
     */
    private static List<EvidenceEntry> toEvidence(List<EvidenceItem> items, String criterionVersion) {
        List<EvidenceEntry> entries = new ArrayList<>(items.size());
        for (EvidenceItem item : items) {
            entries.add(new EvidenceEntry(
                    item.subjectKind().name(),
                    item.subjectKey(),
                    item.sourceRef() == null ? null : item.sourceRef().entityType(),
                    item.sourceRef() == null ? null : item.sourceRef().recordId(),
                    item.eventDate(),
                    item.modality(),
                    item.cnes(),
                    item.ine(),
                    item.cbo(),
                    item.component(),
                    item.decision().name(),
                    item.reasonCode(),
                    item.points() == null ? null : item.points().toString(),
                    criterionVersion));
        }
        return entries;
    }
}
