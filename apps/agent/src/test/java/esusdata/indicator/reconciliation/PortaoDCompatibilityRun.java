package esusdata.indicator.reconciliation;

import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.CompatibilityDossierWriter.WrittenDossier;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Row;
import esusdata.indicator.reconciliation.MethodologyCompatibilityEvaluator.Evidence;
import esusdata.indicator.reconciliation.NormalizedReference.FinalRow;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.NormalizedReference.TeamRow;
import esusdata.indicator.reconciliation.OfficialFieldComparison.TeamPair;
import esusdata.indicator.reconciliation.OfficialFieldComparison.Values;
import esusdata.indicator.reconciliation.PortaoDDiagnosticRun.Reference;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.run.acquisition.PecAcquisitionException;
import esusdata.run.worker.AcquisitionInputs;
import esusdata.run.worker.QuadrimestreContext;
import esusdata.run.worker.ReferenceScopedExtracts;
import esusdata.run.worker.ReferenceScopedExtracts.NotaFinalContext;
import esusdata.run.worker.ReferenceScopedExtracts.NotaFinalInputs;
import esusdata.run.worker.ReferenceScopedExtracts.QuadrimestreInputs;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.run.worker.SourceIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The compatibility run of the Portão D (ADR 0034 §5, spec 2026-10-08 §7.2 stage B): for every
 * captured period, every captured revision of the official reference of C1 to C7 and then the Nota
 * Final of the same quadrimestre get a {@link CompatibilityDossier}. Nothing here is compared with a
 * threshold and nothing decides D: a dossier says whether a reference is methodologically comparable
 * with the compiled rule, and only a later stage (the gate) reads it.
 *
 * <p>What a dossier stands on, per reference:
 *
 * <ul>
 *   <li>the stored manifest and reference, verified again ({@link ReferenceArtifactStore#load});
 *   <li>the methodology profile of the compiled {@code pack@rule_version}. Without one there is no
 *       dossier: it is a {@link Kind#GAP}, never a verdict;
 *   <li>the extracts of the four months, through {@link ReferenceScopedExtracts}, keyed by the hash of
 *       the manifest: a live run acquires what the cache lacks (read-only, through the {@link
 *       AcquisitionInputs} it is given), a replay or a gate check is given {@link
 *       AcquisitionInputs#none()} and cannot read anything else;
 *   <li>every probe the profile names, dimensions and conventions, on one {@link ProbeContext}. A
 *       probe that fails is a line of the local log and no result, which the evaluator reads as
 *       unobserved;
 *   <li>how the type of the revision's teams was read, and the local figures against the official
 *       ones (diagnostic only; a figure that cannot be derived is not compared).
 * </ul>
 *
 * <p>The Nota Final takes the verdicts of the seven dossiers of the revisions its manifest names as
 * siblings, produced in the same run, and reads the seven packs' partitions without a PEC.
 *
 * <p>The dossiers hold no timestamp: the same inputs give byte-identical files ({@link #replay}). A
 * period the local source lacks months of is a {@link Kind#GAP} with the months it lacks. The local
 * detail (INEs) and the local log go to a separate directory that is never under {@code docs/}.
 *
 * <p>{@link GateCheck} is the third stage: it reads these dossiers, computes the gate verdict of every
 * GATE reference from the cache, decides each reference set ({@link ReferenceSetVerdict}) and records
 * D in the registry.
 */
final class PortaoDCompatibilityRun {

    private static final String PACK_OF_FILE = "(?:c[1-7]|ciii)";
    private static final Pattern DOSSIER_FILE = Pattern.compile("(?<id>[a-z]{2}-\\d{7}-(?<year>\\d{4})q(?<index>[1-3])-"
            + PACK_OF_FILE + "-team-r[1-9]\\d*)-[a-z0-9-]+\\.(?:json|md)");
    private static final String LOCAL_DETAIL_DIR = "detalhe";
    private static final String JSON = ".json";
    private static final String MARKDOWN = ".md";
    private static final String NOT_AVAILABLE = "none";

    private final ReferenceArtifactStore store;
    private final ReferenceScopedExtracts extracts;
    private final String municipalityIbge;
    private final Map<Quadrimestre, List<Reference>> byPeriod;
    private final Profiles profiles;
    private final Function<String, List<MethodologyProbe>> probes;
    private final Coverage coverage;

    /** How the type of the revision's teams is read in the inputs of a pack: {@link TeamTypeCoverage#of}. */
    @FunctionalInterface
    interface Coverage {

        TeamTypeCoverage of(List<PackInput> inputs, Map<String, String> revisionTeams);
    }

    /** The methodology profile of a compiled rule, when there is one. */
    @FunctionalInterface
    interface Profiles {

        Optional<MethodologyProfile> of(String packId, String ruleVersion);

        /** The profiles of {@code registry}. */
        static Profiles of(MethodologyProfileRegistry registry) {
            return (packId, ruleVersion) -> registry.profiles().stream()
                    .filter(profile -> profile.pack().equals(packId)
                            && profile.ruleVersion().equals(ruleVersion))
                    .findFirst();
        }
    }

    /** Which source the partitions of a reference revision were, or are to be, read from. */
    @FunctionalInterface
    interface Sources {

        SourceIdentity of(String municipalityIbge, Quadrimestre quadrimestre, String referenceSha, String ruleVersion)
                throws IOException;

        /** The one source a live run reads, proved by the read-only preflight. */
        static Sources fixed(SourceIdentity source) {
            return (municipality, quadrimestre, referenceSha, ruleVersion) -> {
                if (!source.municipalityIbge().equals(municipality)) {
                    throw new IllegalStateException("the manifests are about another municipality than the source");
                }
                return source;
            };
        }

        /**
         * The source the cache itself recorded, for a run that has no PEC: exactly one, or a refusal.
         */
        static Sources recorded(ReferenceScopedExtracts extracts) {
            return (municipality, quadrimestre, referenceSha, ruleVersion) -> {
                Set<SourceIdentity> recorded =
                        extracts.recordedSources(municipality, quadrimestre, referenceSha, ruleVersion);
                if (recorded.size() != 1) {
                    throw new IllegalStateException("the cache holds " + recorded.size() + " sources for " + ruleVersion
                            + " of " + quadrimestre + ", not exactly one: nothing is guessed");
                }
                return recorded.iterator().next();
            };
        }
    }

    /** What became of one reference in a run. */
    enum Kind {
        /** A dossier was written. */
        DOSSIER,
        /** There is nothing to decide on: a missing profile, local month, reference or sibling. */
        GAP,
        /** The dossier could not be produced; a bug or a broken cache, never a verdict. */
        ERROR
    }

    /**
     * One reference of a run.
     *
     * @param referenceId the reference revision, or {@code none} for a pack with no captured reference
     * @param pack the pack code
     * @param verdict the verdict of the dossier, when there is one
     * @param dossierSha256 the SHA-256 of the dossier JSON, when there is one
     * @param detail why not, masked of long numbers; empty for a dossier
     */
    record Outcome(
            Quadrimestre period,
            String referenceId,
            String pack,
            Kind kind,
            Optional<ReferenceCompatibility> verdict,
            Optional<String> dossierSha256,
            String detail) {

        static Outcome dossier(Reference reference, CompatibilityDossier dossier, WrittenDossier written) {
            return new Outcome(
                    reference.period(),
                    reference.id(),
                    reference.pack().code(),
                    Kind.DOSSIER,
                    Optional.of(dossier.verdict()),
                    Optional.of(written.jsonSha256()),
                    "");
        }

        static Outcome of(Reference reference, Kind kind, String detail) {
            return new Outcome(
                    reference.period(),
                    reference.id(),
                    reference.pack().code(),
                    kind,
                    Optional.empty(),
                    Optional.empty(),
                    Row.redacted(detail));
        }

        static Outcome absent(Quadrimestre period, GatePack pack, String detail) {
            return new Outcome(
                    period, NOT_AVAILABLE, pack.code(), Kind.GAP, Optional.empty(), Optional.empty(), detail);
        }
    }

    /**
     * What a run did.
     *
     * @param plan which periods ran and which lacked local months
     * @param outcomes every reference of every period, in order
     * @param localLog the lines for the local log: failed and missing probes, with their messages,
     *     which may name a team. Never versioned
     */
    record Report(List<PeriodExecutionPlan> plan, List<Outcome> outcomes, List<String> localLog) {

        Report {
            plan = List.copyOf(plan);
            outcomes = List.copyOf(outcomes);
            localLog = List.copyOf(localLog);
        }

        long count(Kind kind) {
            return outcomes.stream().filter(outcome -> outcome.kind() == kind).count();
        }

        List<Outcome> errors() {
            return of(Kind.ERROR);
        }

        /** The references with nothing to decide on: no dossier, and no verdict either. */
        List<Outcome> gaps() {
            return of(Kind.GAP);
        }

        private List<Outcome> of(Kind kind) {
            return outcomes.stream().filter(outcome -> outcome.kind() == kind).toList();
        }

        long countVerdict(ReferenceCompatibility verdict) {
            return outcomes.stream()
                    .filter(outcome -> outcome.verdict().filter(verdict::equals).isPresent())
                    .count();
        }
    }

    /**
     * Where a run writes.
     *
     * @param dossierDir the dossiers, {@code <reference-id>-<pack>.json} and {@code .md}; it may be
     *     the versioned directory
     * @param localDir the per-team detail of the dossiers, which carries INEs: never under {@code docs/}
     */
    record Output(Path dossierDir, Path localDir) {}

    private PortaoDCompatibilityRun(
            ReferenceArtifactStore store,
            ReferenceScopedExtracts extracts,
            String municipalityIbge,
            Map<Quadrimestre, List<Reference>> byPeriod,
            Profiles profiles,
            Function<String, List<MethodologyProbe>> probes,
            Coverage coverage) {
        this.store = store;
        this.extracts = extracts;
        this.municipalityIbge = municipalityIbge;
        this.byPeriod = byPeriod;
        this.profiles = profiles;
        this.probes = probes;
        this.coverage = coverage;
    }

    /**
     * Reads the manifests of {@code manifestsDir} ({@link PortaoDDiagnosticRun#captured}).
     *
     * @param requested the periods to run; empty runs every captured period. A requested period no
     *     manifest is about is refused rather than silently dropped
     * @param probes the probes to run on a pack, by pack id, e.g. {@code MethodologyProbeCatalog::forPack}
     */
    static PortaoDCompatibilityRun open(
            Path manifestsDir,
            Path artifactDir,
            Set<Quadrimestre> requested,
            ReferenceScopedExtracts extracts,
            Profiles profiles,
            Function<String, List<MethodologyProbe>> probes)
            throws IOException {
        return open(manifestsDir, artifactDir, requested, extracts, profiles, probes, TeamTypeCoverage::of);
    }

    /** As the other {@code open}, reading the type of the teams as {@code coverage} says. */
    static PortaoDCompatibilityRun open(
            Path manifestsDir,
            Path artifactDir,
            Set<Quadrimestre> requested,
            ReferenceScopedExtracts extracts,
            Profiles profiles,
            Function<String, List<MethodologyProbe>> probes,
            Coverage coverage)
            throws IOException {
        List<Reference> references = PortaoDDiagnosticRun.captured(manifestsDir);
        Map<Quadrimestre, List<Reference>> byPeriod = new TreeMap<>();
        for (Reference reference : references) {
            byPeriod.computeIfAbsent(reference.period(), unused -> new ArrayList<>())
                    .add(reference);
        }
        for (Quadrimestre period : requested) {
            if (!byPeriod.containsKey(period)) {
                throw new IllegalArgumentException(
                        "the requested period " + period + " has no captured reference: capture it first");
            }
        }
        if (!requested.isEmpty()) {
            byPeriod.keySet().retainAll(requested);
        }
        byPeriod.values().forEach(list -> list.sort(PortaoDDiagnosticRun.order()));
        return new PortaoDCompatibilityRun(
                new ReferenceArtifactStore(artifactDir),
                extracts,
                references.getFirst().manifest().municipalityIbge(),
                byPeriod,
                profiles,
                probes,
                coverage);
    }

    /** The captured periods this run covers, oldest first. */
    List<Quadrimestre> periods() {
        return List.copyOf(byPeriod.keySet());
    }

    /** The municipality every manifest is about. */
    String municipalityIbge() {
        return municipalityIbge;
    }

    /**
     * Produces the dossiers of every captured period the local source can execute and reports the
     * others.
     *
     * @param sources where each reference's extracts were, or are to be, read from
     * @param localCoverage the months the local source holds for the municipality
     * @param acquisition what may be read from the PEC for a partition the cache lacks; {@link
     *     AcquisitionInputs#none()} reads nothing
     */
    Report run(Sources sources, Set<YearMonth> localCoverage, AcquisitionInputs acquisition, Output output) {
        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(periods(), localCoverage);
        List<Outcome> outcomes = new ArrayList<>();
        List<String> log = new ArrayList<>();
        for (PeriodExecutionPlan period : plan) {
            if (period.runs()) {
                outcomes.addAll(runPeriod(period.quadrimestre(), sources, acquisition, output, log));
            } else {
                outcomes.addAll(notRun(period));
            }
        }
        return new Report(plan, outcomes, log);
    }

    private List<Outcome> notRun(PeriodExecutionPlan period) {
        String reason = "meses locais ausentes: " + period.missingMonths();
        List<Outcome> outcomes = new ArrayList<>();
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            List<Reference> references = referencesOf(period.quadrimestre(), pack);
            if (references.isEmpty()) {
                outcomes.add(Outcome.absent(period.quadrimestre(), pack, reason));
            }
            references.forEach(reference -> outcomes.add(Outcome.of(reference, Kind.GAP, reason)));
        }
        return outcomes;
    }

    private List<Outcome> runPeriod(
            Quadrimestre period, Sources sources, AcquisitionInputs acquisition, Output output, List<String> log) {
        List<Outcome> outcomes = new ArrayList<>();
        Map<String, ReferenceCompatibility> verdicts = new TreeMap<>();
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            List<Reference> references = referencesOf(period, pack);
            if (references.isEmpty()) {
                outcomes.add(Outcome.absent(period, pack, "nenhuma referência capturada para este pack e período"));
            }
            for (Reference reference : references) {
                Outcome outcome = pack.isNotaFinal()
                        ? notaFinalDossier(reference, sources, output, verdicts, log)
                        : packDossier(reference, sources, acquisition, output, log);
                outcome.verdict().ifPresent(verdict -> verdicts.put(reference.id(), verdict));
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    private List<Reference> referencesOf(Quadrimestre period, GatePack pack) {
        return byPeriod.get(period).stream()
                .filter(reference -> reference.pack().equals(pack))
                .toList();
    }

    // ---- C1 to C7

    private Outcome packDossier(
            Reference reference, Sources sources, AcquisitionInputs acquisition, Output output, List<String> log) {
        try {
            IndicatorRule rule = PortaoDDiagnosticRun.ruleOf(reference.pack());
            String ruleVersion = rule.descriptor().ruleVersion();
            NormalizedReference stored = store.load(reference.manifest());
            ValidatedReference official = ValidatedReference.fromStored(stored, reference.pack());
            SourceIdentity source = sources.of(municipalityIbge, reference.period(), reference.sha256(), ruleVersion);
            QuadrimestreInputs inputs = extracts.loadOrAcquireQuadrimestre(
                    new QuadrimestreContext(
                            municipalityIbge,
                            reference.period(),
                            reference.sha256(),
                            reference.pack().packId(),
                            ruleVersion,
                            source),
                    acquisition);
            // The extracts are kept even for a pack without a profile: the Nota Final reads them.
            Optional<MethodologyProfile> profile = profiles.of(reference.pack().packId(), ruleVersion);
            if (profile.isEmpty()) {
                return Outcome.of(reference, Kind.GAP, "no methodology profile for " + ruleVersion);
            }
            List<RuleOutcome> baseline = PortaoDDiagnosticRun.baseline(inputs);
            Map<YearMonth, List<TeamResult>> monthly = PortaoDDiagnosticRun.monthly(baseline, reference.period());
            Probed probed = probe(
                    reference,
                    profile.get(),
                    () -> new PackProbeContext(
                            reference.period(),
                            inputs.months(),
                            baseline,
                            official,
                            reference.manifest(),
                            inputs.localSourceFingerprint()),
                    log);
            Evidence evidence = evidence(
                    reference,
                    profile.get(),
                    source,
                    official,
                    inputs.localSourceFingerprint(),
                    probed,
                    coverage.of(inputs.months(), revisionTeams(official)),
                    OfficialFieldComparison.of(pairs(stored, LocalClasses.fieldsOf(rule, reference.period(), monthly))),
                    Map.of());
            return write(reference, evidence, output);
        } catch (IOException
                | IllegalStateException
                | IllegalArgumentException
                | UncheckedIOException
                | PecAcquisitionException failure) {
            return failed(reference, failure, log);
        }
    }

    // ---- the Nota Final

    /** What is kept of one pack's four months for the Nota Final: its monthly results and the type reading. */
    private record Reduced(Map<YearMonth, List<TeamResult>> monthly, TeamTypeCoverage coverage) {}

    private Outcome notaFinalDossier(
            Reference reference,
            Sources sources,
            Output output,
            Map<String, ReferenceCompatibility> verdicts,
            List<String> log) {
        Map<String, Reference> siblings = siblingsOf(reference);
        if (siblings.size() != GatePack.all().size()) {
            return Outcome.of(
                    reference,
                    Kind.GAP,
                    "faltam as referências do mesmo export oficial para: " + missingPacks(siblings));
        }
        try {
            Optional<MethodologyProfile> profile = profiles.of(ComponentIII.ID, ComponentIII.RULE_VERSION);
            if (profile.isEmpty()) {
                return Outcome.of(reference, Kind.GAP, "no methodology profile for " + ComponentIII.RULE_VERSION);
            }
            NormalizedReference stored = store.load(reference.manifest());
            ValidatedReference official = ValidatedReference.fromStored(stored, reference.pack());
            Reference first = siblings.get(GatePack.all().getFirst().packId());
            SourceIdentity source = sources.of(
                    municipalityIbge,
                    reference.period(),
                    first.sha256(),
                    PortaoDDiagnosticRun.ruleOf(first.pack()).descriptor().ruleVersion());
            Map<String, String> shas = new TreeMap<>();
            siblings.forEach((packId, sibling) -> shas.put(packId, sibling.sha256()));
            Map<String, String> revision = revisionTeams(official);
            NotaFinalInputs<Reduced> inputs = extracts.loadNotaFinal(
                    new NotaFinalContext(municipalityIbge, reference.period(), source, shas),
                    months -> new Reduced(
                            PortaoDDiagnosticRun.monthly(PortaoDDiagnosticRun.baseline(months), reference.period()),
                            coverage.of(months.months(), revision)));
            Map<String, Map<YearMonth, List<TeamResult>>> monthlyByPack = new LinkedHashMap<>();
            inputs.byPack().forEach((packId, reduced) -> monthlyByPack.put(packId, reduced.monthly()));
            Probed probed = probe(
                    reference,
                    profile.get(),
                    () -> new NotaFinalProbeContext(
                            reference.period(),
                            monthlyByPack,
                            official,
                            reference.manifest(),
                            inputs.localSourceFingerprint()),
                    log);
            Map<String, ReferenceCompatibility> siblingVerdicts = new TreeMap<>();
            siblings.forEach((packId, sibling) -> Optional.ofNullable(verdicts.get(sibling.id()))
                    .ifPresent(verdict -> siblingVerdicts.put(packId, verdict)));
            Evidence evidence = evidence(
                    reference,
                    profile.get(),
                    source,
                    official,
                    inputs.localSourceFingerprint(),
                    probed,
                    sum(inputs.byPack().values().stream().map(Reduced::coverage).toList()),
                    OfficialFieldComparison.of(
                            pairs(stored, LocalClasses.notaFinalFieldsOf(reference.period(), monthlyByPack))),
                    siblingVerdicts);
            return write(reference, evidence, output);
        } catch (IOException
                | IllegalStateException
                | IllegalArgumentException
                | UncheckedIOException
                | PecAcquisitionException failure) {
            return failed(reference, failure, log);
        }
    }

    /** As the diagnostic does: the revision of each of C1 to C7 the download of the Nota Final named. */
    private Map<String, Reference> siblingsOf(Reference notaFinal) {
        Set<String> named = Set.copyOf(notaFinal.manifest().siblingReferenceIds());
        Map<String, Reference> siblings = new LinkedHashMap<>();
        for (GatePack pack : GatePack.all()) {
            referencesOf(notaFinal.period(), pack).stream()
                    .filter(reference -> named.contains(reference.id()))
                    .findFirst()
                    .ifPresent(reference -> siblings.put(pack.packId(), reference));
        }
        return siblings;
    }

    private static List<String> missingPacks(Map<String, Reference> siblings) {
        return GatePack.all().stream()
                .filter(pack -> !siblings.containsKey(pack.packId()))
                .map(GatePack::code)
                .toList();
    }

    private static TeamTypeCoverage sum(List<TeamTypeCoverage> parts) {
        int teamMonths = 0;
        int audited = 0;
        int agreeing = 0;
        int disagreeing = 0;
        int unresolved = 0;
        int typeDisagreeing = 0;
        for (TeamTypeCoverage part : parts) {
            teamMonths += part.teamMonths();
            audited += part.audited();
            agreeing += part.fallbackAgreeing();
            disagreeing += part.fallbackDisagreeing();
            unresolved += part.unresolved();
            typeDisagreeing += part.typeDisagreeing();
        }
        return new TeamTypeCoverage(teamMonths, audited, agreeing, disagreeing, unresolved, typeDisagreeing);
    }

    // ---- evidence

    /** The results of the probes a profile names, by probe id. */
    private record Probed(Map<String, ProbeResult> dimensions, Map<String, ProbeResult> conventions) {}

    /**
     * Runs every probe the profile names on one context. A context that cannot be built, a probe the
     * catalog lacks and a probe that fails are lines of the local log and no result.
     */
    private Probed probe(
            Reference reference, MethodologyProfile profile, Supplier<ProbeContext> contextOf, List<String> log) {
        ProbeContext context = null;
        try {
            context = contextOf.get();
        } catch (IllegalArgumentException | IllegalStateException failure) {
            log.add(label(reference) + ": no probe context: " + failure);
        }
        List<MethodologyProbe> catalog = probes.apply(reference.pack().packId());
        Map<String, ProbeResult> dimensions = resultsOf(reference, profile.requiredProbeIds(), catalog, context, log);
        Map<String, ProbeResult> conventions =
                resultsOf(reference, profile.conventionProbeIds(), catalog, context, log);
        return new Probed(dimensions, conventions);
    }

    private static Map<String, ProbeResult> resultsOf(
            Reference reference,
            List<String> ids,
            List<MethodologyProbe> catalog,
            ProbeContext context,
            List<String> log) {
        Map<String, ProbeResult> results = new LinkedHashMap<>();
        for (String id : ids) {
            Optional<MethodologyProbe> probe = catalog.stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst();
            if (probe.isEmpty()) {
                log.add(label(reference) + ": probe " + id + " is not in the catalog");
            } else if (context != null) {
                evaluate(reference, probe.get(), context, log).ifPresent(result -> results.put(id, result));
            }
        }
        return results;
    }

    private static Optional<ProbeResult> evaluate(
            Reference reference, MethodologyProbe probe, ProbeContext context, List<String> log) {
        try {
            ProbeResult result = probe.evaluate(context);
            if (probe.id().equals(result.probeId())) {
                return Optional.of(result);
            }
            log.add(label(reference) + ": probe " + probe.id() + " answered as " + result.probeId());
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException failure) {
            log.add(label(reference) + ": probe " + probe.id() + " failed: " + failure);
        }
        return Optional.empty();
    }

    private static Evidence evidence(
            Reference reference,
            MethodologyProfile profile,
            SourceIdentity source,
            ValidatedReference official,
            String fingerprint,
            Probed probed,
            TeamTypeCoverage coverage,
            OfficialFieldComparison fields,
            Map<String, ReferenceCompatibility> siblingVerdicts) {
        return new Evidence(
                profile,
                SiapsFormats.quadrimestre(reference.period()),
                reference.id(),
                reference.sha256(),
                fingerprint,
                scopeMismatch(reference, profile, source, official),
                official.universe() == ValidatedReference.UniverseConfidence.OFFICIAL,
                probed.dimensions(),
                probed.conventions(),
                coverage,
                fields,
                siblingVerdicts);
    }

    /**
     * Why the manifest, the pack, the rule version, the quadrimestre and the source do not agree, in
     * words that name a field and never a value (a municipality code would be refused in the dossier).
     */
    private static Optional<String> scopeMismatch(
            Reference reference, MethodologyProfile profile, SourceIdentity source, ValidatedReference official) {
        SiapsReferenceManifest manifest = reference.manifest();
        List<String> problems = new ArrayList<>();
        if (!profile.pack().equals(reference.pack().packId())) {
            problems.add("the profile is of another pack");
        }
        if (!manifest.quadrimestre().equals(SiapsFormats.quadrimestre(reference.period()))) {
            problems.add("the manifest is of another quadrimestre");
        }
        if (!manifest.indicatorCodes().equals(List.of(reference.pack().siapsCode()))) {
            problems.add("the manifest is of another indicator");
        }
        if (!manifest.municipalityIbge().equals(source.municipalityIbge())) {
            problems.add("the manifest is of another municipality than the source");
        }
        if (!official.municipalityIbge().equals(SiapsFormats.ibgeOfSiaps(manifest.municipalityIbge()))
                || !official.quadrimestre().equals(manifest.quadrimestre())) {
            problems.add("the stored reference is not the one the manifest describes");
        }
        return problems.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", problems));
    }

    private static Map<String, String> revisionTeams(ValidatedReference official) {
        Map<String, String> teams = new TreeMap<>();
        official.teams().forEach(team -> teams.put(team.ine(), team.teamType()));
        return teams;
    }

    /** One pair per team of the official rows: the local figures of that INE, or none at all. */
    private static List<TeamPair> pairs(NormalizedReference stored, Map<String, Values> local) {
        Values none = new Values(null, null, null, null);
        List<TeamPair> pairs = new ArrayList<>();
        for (TeamRow row : stored.rows()) {
            Values official = switch (row) {
                case IndicatorRow indicator -> new Values(indicator.result(), indicator.concept(), null, null);
                case FinalRow last -> new Values(null, null, last.finalNote(), last.finalClass());
            };
            pairs.add(new TeamPair(row.ine(), local.getOrDefault(row.ine(), none), official));
        }
        return pairs;
    }

    // ---- writing

    private static Outcome write(Reference reference, Evidence evidence, Output output) throws IOException {
        CompatibilityDossier dossier = MethodologyCompatibilityEvaluator.evaluate(evidence);
        String name = reference.id() + "-" + reference.pack().packId();
        WrittenDossier written = CompatibilityDossierWriter.write(
                output.dossierDir().resolve(name + JSON),
                output.dossierDir().resolve(name + MARKDOWN),
                dossier,
                Optional.of(output.localDir().resolve(LOCAL_DETAIL_DIR).resolve(name + ".txt")));
        return Outcome.dossier(reference, dossier, written);
    }

    private static Outcome failed(Reference reference, Exception failure, List<String> log) {
        log.add(label(reference) + ": " + failure);
        return Outcome.of(reference, Kind.ERROR, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private static String label(Reference reference) {
        return reference.id() + " " + reference.pack().code();
    }

    // ---- the local source a gate check reads

    /**
     * The fingerprint of the local source the cache holds for a reference: what a dossier must have
     * been decided on for the gate to run against that very source. It reads the cache only.
     *
     * @throws IllegalStateException when the cache lacks any partition of it or holds more than one source
     */
    String cachedFingerprint(Reference reference, Sources sources) throws IOException {
        if (reference.pack().isNotaFinal()) {
            return cachedNotaFinal(reference, sources, months -> NOT_AVAILABLE).localSourceFingerprint();
        }
        return cachedPack(reference, sources).localSourceFingerprint();
    }

    /**
     * The Portão D verdict of one reference against the cache alone: the same comparison as the
     * diagnostic run, with {@link ReferencePurpose#GATE}. The Nota Final goes through the same derived
     * fingerprint as its dossier, the one of the seven packs' partitions. Nothing is acquired and no
     * PEC is read; whatever the cache lacks is a refusal, which the caller does not catch.
     *
     * @throws IllegalStateException when the cache lacks any partition of it or holds more than one source
     */
    PackVerdict gateVerdict(Reference reference, Sources sources) throws IOException {
        ValidatedReference official = ValidatedReference.fromStored(store.load(reference.manifest()), reference.pack());
        if (reference.pack().isNotaFinal()) {
            NotaFinalInputs<Map<YearMonth, List<TeamResult>>> inputs = cachedNotaFinal(
                    reference,
                    sources,
                    months -> PortaoDDiagnosticRun.monthly(PortaoDDiagnosticRun.baseline(months), reference.period()));
            return PackVerdict.evaluate(
                    reference.pack(),
                    ComponentIII.RULE_VERSION,
                    ReferencePurpose.GATE,
                    official,
                    LocalClasses.ofNotaFinal(reference.period(), inputs.byPack()),
                    inputs.localSourceFingerprint());
        }
        IndicatorRule rule = PortaoDDiagnosticRun.ruleOf(reference.pack());
        QuadrimestreInputs inputs = cachedPack(reference, sources);
        LocalClasses local = LocalClasses.of(
                rule,
                reference.period(),
                PortaoDDiagnosticRun.monthly(PortaoDDiagnosticRun.baseline(inputs), reference.period()));
        return PackVerdict.evaluate(
                reference.pack(),
                rule.descriptor().ruleVersion(),
                ReferencePurpose.GATE,
                official,
                local,
                inputs.localSourceFingerprint());
    }

    /** The four months of one of C1 to C7 as the cache holds them, with {@link AcquisitionInputs#none()}. */
    private QuadrimestreInputs cachedPack(Reference reference, Sources sources) throws IOException {
        String ruleVersion =
                PortaoDDiagnosticRun.ruleOf(reference.pack()).descriptor().ruleVersion();
        return extracts.loadOrAcquireQuadrimestre(
                new QuadrimestreContext(
                        municipalityIbge,
                        reference.period(),
                        reference.sha256(),
                        reference.pack().packId(),
                        ruleVersion,
                        sources.of(municipalityIbge, reference.period(), reference.sha256(), ruleVersion)),
                AcquisitionInputs.none());
    }

    /** The partitions of the seven packs the Nota Final reads, as the cache holds them. */
    private <T> NotaFinalInputs<T> cachedNotaFinal(
            Reference reference, Sources sources, Function<QuadrimestreInputs, T> reduce) throws IOException {
        Map<String, Reference> siblings = siblingsOf(reference);
        if (siblings.size() != GatePack.all().size()) {
            throw new IllegalStateException("the cache cannot stand for the Nota Final: siblings are missing");
        }
        Reference first = siblings.get(GatePack.all().getFirst().packId());
        Map<String, String> shas = new TreeMap<>();
        siblings.forEach((packId, sibling) -> shas.put(packId, sibling.sha256()));
        SourceIdentity source = sources.of(
                municipalityIbge,
                reference.period(),
                first.sha256(),
                PortaoDDiagnosticRun.ruleOf(first.pack()).descriptor().ruleVersion());
        return extracts.loadNotaFinal(new NotaFinalContext(municipalityIbge, reference.period(), source, shas), reduce);
    }

    /** The captured reference with this id. */
    Optional<Reference> reference(String referenceId) {
        return byPeriod.values().stream()
                .flatMap(List::stream)
                .filter(reference -> reference.id().equals(referenceId))
                .findFirst();
    }

    // ---- replay

    /**
     * What a replay found.
     *
     * @param regenerated the dossier files it regenerated
     * @param missing files of the dossier directory it did not regenerate
     * @param extra files it regenerated that the dossier directory lacks
     * @param different files whose bytes differ
     * @param errors references it could not regenerate
     * @param gaps references of a replayed period with nothing to decide on (a missing profile,
     *     sibling or reference): the evidence of that period is incomplete, so the replay fails
     */
    record ReplayResult(
            List<String> regenerated,
            List<String> missing,
            List<String> extra,
            List<String> different,
            List<Outcome> errors,
            List<Outcome> gaps) {

        ReplayResult {
            regenerated = List.copyOf(regenerated);
            missing = List.copyOf(missing);
            extra = List.copyOf(extra);
            different = List.copyOf(different);
            errors = List.copyOf(errors);
            gaps = List.copyOf(gaps);
        }

        boolean identical() {
            return missing.isEmpty()
                    && extra.isEmpty()
                    && different.isEmpty()
                    && errors.isEmpty()
                    && gaps.isEmpty()
                    && !regenerated.isEmpty();
        }
    }

    /**
     * Regenerates the dossiers of every period that has one in {@code dossierDir} from the cached
     * artifacts alone ({@link AcquisitionInputs#none()}: it refuses to acquire, and needs no PEC),
     * into {@code scratch}, and compares both the JSON and the Markdown byte for byte. A file on one
     * side only, a reference it cannot regenerate or that is a gap, and a different byte are all
     * failures. A period with no dossier at all is not replayed: that the campaign left no gap is
     * what the compatibility run asserts.
     *
     * @param scratch an empty directory outside {@code docs/}; the regenerated dossiers and their
     *     local detail are written there
     */
    static ReplayResult replay(
            Path manifestsDir,
            Path artifactDir,
            ReferenceScopedExtracts extracts,
            Profiles profiles,
            Function<String, List<MethodologyProbe>> probes,
            Path dossierDir,
            Path scratch)
            throws IOException {
        return replay(manifestsDir, artifactDir, extracts, profiles, probes, TeamTypeCoverage::of, dossierDir, scratch);
    }

    /** As the other {@code replay}, reading the type of the teams as {@code coverage} says. */
    static ReplayResult replay(
            Path manifestsDir,
            Path artifactDir,
            ReferenceScopedExtracts extracts,
            Profiles profiles,
            Function<String, List<MethodologyProbe>> probes,
            Coverage coverage,
            Path dossierDir,
            Path scratch)
            throws IOException {
        Set<Quadrimestre> periods = new TreeSet<>();
        for (Path file : dossierFiles(dossierDir)) {
            Matcher name = DOSSIER_FILE.matcher(file.getFileName().toString());
            if (!name.matches()) {
                throw new IllegalArgumentException(
                        "a file of the dossier directory is not named like a dossier: " + file.getFileName());
            }
            periods.add(new Quadrimestre(Integer.parseInt(name.group("year")), Integer.parseInt(name.group("index"))));
        }
        if (periods.isEmpty()) {
            throw new IllegalStateException("the dossier directory has no dossier to replay");
        }
        PortaoDCompatibilityRun run = open(manifestsDir, artifactDir, periods, extracts, profiles, probes, coverage);
        Report report = run.run(
                Sources.recorded(extracts),
                allMonthsOf(run.periods()),
                AcquisitionInputs.none(),
                new Output(scratch, scratch));
        return compare(dossierDir, scratch, report);
    }

    private static Set<YearMonth> allMonthsOf(List<Quadrimestre> periods) {
        return periods.stream().flatMap(period -> period.months().stream()).collect(Collectors.toSet());
    }

    private static List<Path> dossierFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.endsWith(JSON) || name.endsWith(MARKDOWN);
                    })
                    .sorted()
                    .toList();
        }
    }

    private static ReplayResult compare(Path dossierDir, Path scratch, Report report) throws IOException {
        Set<String> kept = namesOf(dossierFiles(dossierDir));
        Set<String> regenerated = namesOf(dossierFiles(scratch));
        List<String> missing =
                kept.stream().filter(name -> !regenerated.contains(name)).toList();
        List<String> extra =
                regenerated.stream().filter(name -> !kept.contains(name)).toList();
        List<String> different = new ArrayList<>();
        for (String name : kept) {
            if (regenerated.contains(name)
                    && !Arrays.equals(
                            Files.readAllBytes(dossierDir.resolve(name)), Files.readAllBytes(scratch.resolve(name)))) {
                different.add(name);
            }
        }
        return new ReplayResult(List.copyOf(regenerated), missing, extra, different, report.errors(), report.gaps());
    }

    private static Set<String> namesOf(List<Path> files) {
        return files.stream().map(file -> file.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
    }

    // ---- the gate

    /**
     * The third stage (spec §7.2 stage C and §16.2): the GATE declarations of the policy in the
     * working tree, each with the dossier that authorizes it and the local source that dossier was
     * decided on, then the Portão D verdict of every reference set, recorded in the registry.
     *
     * <p>It refuses a working tree that is not clean (the evidence that pre-registers the set is a
     * commit, and the run is against that commit), a {@code HEAD} that is not an ancestor of {@code
     * origin/main} (the set must have been merged before D is decided) and a GATE declaration that
     * differs from the one {@code HEAD} holds. There is no period to give it: the references are the
     * declarations, and nothing else.
     *
     * <p>The verdict of each GATE reference is computed from the cache alone ({@link
     * PortaoDCompatibilityRun#gateVerdict}); a reference whose declaration, manifest, dossier or cached
     * source does not check out gets none and is {@code PENDING} in its set. An evaluation that throws
     * (a partition gone, a source the cache holds twice) aborts the whole run: every verdict is
     * computed before a single file is written, so there is never a partial registry. Then it writes
     * its summary to the artifact directory, the evidence summary of each decided set under {@code
     * docs/indicadores/portoes/resultado-d/} and {@code D} of every registered pack in {@code
     * contracts/indicators/release-gates.json}. A set with no GATE reference is {@code PENDING}, which
     * is every set until the evidence PR.
     */
    static final class GateCheck {

        /** The policy, relative to the repository root. */
        static final String POLICY_FILE = "contracts/indicators/siaps-reference-policy.json";

        /** The release-gate registry, relative to the repository root. */
        static final String REGISTRY_FILE = "contracts/indicators/release-gates.json";

        private GateCheck() {}

        /** What the check needs of Git: a seam, so that a unit test never starts a process. */
        interface Repository {

            /** True when there is nothing modified, staged or untracked. */
            boolean isClean() throws IOException;

            /** True when {@code HEAD} is an ancestor of {@code origin/main}, as last fetched. */
            boolean isMerged() throws IOException;

            /** The content of a file at {@code HEAD}, if it exists there. */
            Optional<String> committed(String repositoryPath) throws IOException;
        }

        /** One GATE reference. */
        record ReferenceCheck(String referenceId, boolean ok, String detail) {}

        /**
         * One reference set: the hash D would cite, the check of each of its GATE references and the
         * verdict of the set.
         */
        record PackCheck(
                String pack,
                String ruleVersion,
                String gateSetSha256,
                List<ReferenceCheck> references,
                ReferenceSetVerdict verdict) {

            PackCheck {
                references = List.copyOf(references);
            }

            boolean ok() {
                return references.stream().allMatch(ReferenceCheck::ok);
            }
        }

        /** The check of every set. */
        record Summary(List<PackCheck> packs) {

            Summary {
                packs = List.copyOf(packs);
            }

            boolean ok() {
                return packs.stream().allMatch(PackCheck::ok);
            }

            int gateReferences() {
                return packs.stream().mapToInt(pack -> pack.references().size()).sum();
            }

            /** The summary as text, in the order of the policy: no timestamp, so a rerun is identical. */
            String markdown() {
                List<String> lines = new ArrayList<>();
                lines.add("# Conferência do conjunto de gate");
                lines.add("");
                lines.add("Registro de portões: D de cada pack escrito com o veredito do seu conjunto.");
                lines.add("");
                for (PackCheck pack : packs) {
                    lines.add("## " + pack.ruleVersion());
                    lines.add("");
                    lines.add("- gate_set_sha256: `" + pack.gateSetSha256() + "`");
                    lines.add("- veredito do conjunto: **" + pack.verdict().status() + "**"
                            + (pack.verdict().reason().isEmpty()
                                    ? ""
                                    : " (" + pack.verdict().reason() + ")"));
                    if (pack.references().isEmpty()) {
                        lines.add("- conjunto de gate vazio: nenhuma referência GATE declarada");
                    }
                    pack.references().forEach(reference -> lines.add(line(reference)));
                    lines.add("");
                }
                return String.join("\n", lines) + "\n";
            }

            private static String line(ReferenceCheck reference) {
                String detail = reference.detail().isEmpty() ? "" : ": " + reference.detail();
                return "- " + (reference.ok() ? "OK" : "FALHA") + " `" + reference.referenceId() + "`" + detail;
            }
        }

        /**
         * Checks every GATE reference of the policy of {@code repoRoot}, decides every set and
         * records the result.
         *
         * @param extracts the cache of the compatibility campaign, read without a PEC
         * @param compiled the rules of this release
         * @param clock the day D is recorded as checked on
         * @throws IllegalStateException when the working tree is not clean, {@code HEAD} is not merged,
         *     a GATE declaration of the working tree is not that of {@code HEAD}, or the cache cannot
         *     stand for a reference whose declaration, manifest and dossier check out
         */
        static Summary check(
                Path repoRoot,
                Repository repository,
                Path artifactDir,
                ReferenceScopedExtracts extracts,
                Collection<PackDescriptor> compiled,
                Clock clock)
                throws IOException {
            if (!repository.isClean()) {
                throw new IllegalStateException("the working tree is not clean: commit the evidence first,"
                        + " the gate runs against the committed set");
            }
            if (!repository.isMerged()) {
                throw new IllegalStateException("HEAD is not an ancestor of origin/main: the set must be merged"
                        + " (and fetched) before D is decided against it");
            }
            ReferencePolicy working = ReferencePolicy.load(repoRoot.resolve(POLICY_FILE), compiled);
            ReferencePolicy head = ReferencePolicy.fromJson(
                    repository
                            .committed(POLICY_FILE)
                            .orElseThrow(() -> new IllegalStateException("HEAD has no " + POLICY_FILE)),
                    compiled);
            requireSameGateSets(working, head);
            PortaoDCompatibilityRun run = hasGateReferences(working) ? open(repoRoot, artifactDir, extracts) : null;
            List<PackCheck> packs = new ArrayList<>();
            for (ReferenceSet set : working.referenceSets()) {
                packs.add(examine(repoRoot, run, extracts, set));
            }
            // Every verdict exists: from here on files are written.
            Summary summary = new Summary(packs);
            Path summaryFile = artifactDir.resolve("gate").resolve("resumo.md");
            Files.createDirectories(summaryFile.getParent());
            Files.writeString(summaryFile, summary.markdown());
            record(repoRoot, packs.stream().map(PackCheck::verdict).toList(), LocalDate.now(clock));
            return summary;
        }

        private static void requireSameGateSets(ReferencePolicy working, ReferencePolicy head) {
            for (ReferenceSet set : working.referenceSets()) {
                String committed =
                        head.referenceSet(set.pack(), set.ruleVersion()).gateSetSha256();
                if (!committed.equals(set.gateSetSha256())) {
                    throw new IllegalStateException(
                            "the GATE declarations of " + set.ruleVersion() + " differ from the ones HEAD holds");
                }
            }
        }

        private static boolean hasGateReferences(ReferencePolicy policy) {
            return policy.referenceSets().stream()
                    .flatMap(set -> set.references().stream())
                    .anyMatch(declaration -> declaration.purpose() == ReferencePurpose.GATE);
        }

        private static PortaoDCompatibilityRun open(Path repoRoot, Path artifactDir, ReferenceScopedExtracts extracts)
                throws IOException {
            return PortaoDCompatibilityRun.open(
                    repoRoot.resolve(ReferencePolicy.MANIFEST_DIR),
                    artifactDir,
                    Set.of(),
                    extracts,
                    (packId, ruleVersion) -> Optional.empty(),
                    packId -> List.of());
        }

        /** Checks the GATE references of one set and decides it. */
        private static PackCheck examine(
                Path repoRoot, PortaoDCompatibilityRun run, ReferenceScopedExtracts extracts, ReferenceSet set)
                throws IOException {
            List<ReferenceCheck> checks = new ArrayList<>();
            Map<String, DossierEvidence> dossiers = new LinkedHashMap<>();
            Map<String, PackVerdict> verdicts = new LinkedHashMap<>();
            for (ReferenceDeclaration declaration : set.references()) {
                if (declaration.purpose() != ReferencePurpose.GATE) {
                    continue;
                }
                List<String> problems = new ArrayList<>(manifestProblems(repoRoot, declaration));
                Optional<DossierEvidence> dossier = dossierOf(repoRoot, declaration, problems);
                if (dossier.isPresent()) {
                    dossiers.put(declaration.referenceId(), dossier.get());
                    problems.addAll(dossier.get().problems(set, declaration));
                    problems.addAll(sourceProblems(
                            run, extracts, declaration, dossier.get().localSourceFingerprint()));
                }
                checks.add(
                        new ReferenceCheck(declaration.referenceId(), problems.isEmpty(), String.join("; ", problems)));
                if (problems.isEmpty()) {
                    verdicts.put(declaration.referenceId(), gateVerdict(run, extracts, declaration));
                }
            }
            return new PackCheck(
                    set.pack(),
                    set.ruleVersion(),
                    set.gateSetSha256(),
                    checks,
                    ReferenceSetVerdict.aggregate(set, verdicts, dossiers));
        }

        private static PackVerdict gateVerdict(
                PortaoDCompatibilityRun run, ReferenceScopedExtracts extracts, ReferenceDeclaration declaration)
                throws IOException {
            Reference reference = run.reference(declaration.referenceId())
                    .orElseThrow(() -> new IllegalStateException(
                            "the manifest of " + declaration.referenceId() + " is not among the captured ones"));
            return run.gateVerdict(reference, Sources.recorded(extracts));
        }

        private static List<String> manifestProblems(Path repoRoot, ReferenceDeclaration declaration)
                throws IOException {
            Path manifest = repoRoot.resolve(ReferencePolicy.manifestPath(declaration.referenceId()));
            if (!Files.isRegularFile(manifest)) {
                return List.of("the manifest is missing");
            }
            return SummaryWriter.sha256(Files.readAllBytes(manifest)).equals(declaration.referenceManifestSha256())
                    ? List.of()
                    : List.of("the manifest is not the one the declaration pins");
        }

        /** The dossier the declaration cites, as committed; else a problem. */
        private static Optional<DossierEvidence> dossierOf(
                Path repoRoot, ReferenceDeclaration declaration, List<String> problems) throws IOException {
            String ref = declaration.compatibilityEvidenceRef();
            Path file = ref == null ? null : repoRoot.resolve(ref);
            if (file == null || !Files.isRegularFile(file)) {
                problems.add("the cited dossier is missing");
                return Optional.empty();
            }
            Optional<DossierEvidence> dossier = DossierEvidence.read(ref, Files.readAllBytes(file));
            if (dossier.isEmpty()) {
                problems.add("the cited dossier is not a JSON object");
            }
            return dossier;
        }

        /** The fingerprint of the extracts the cache holds is the one the dossier was decided on. */
        private static List<String> sourceProblems(
                PortaoDCompatibilityRun run,
                ReferenceScopedExtracts extracts,
                ReferenceDeclaration declaration,
                String decidedOn) {
            Optional<Reference> reference = run.reference(declaration.referenceId());
            if (reference.isEmpty()) {
                return List.of("the manifest of the reference is not among the captured ones");
            }
            try {
                String cached = run.cachedFingerprint(reference.get(), Sources.recorded(extracts));
                return cached.equals(decidedOn)
                        ? List.of()
                        : List.of("the local source in the cache is not the one the dossier was decided on");
            } catch (IOException | IllegalStateException | IllegalArgumentException | UncheckedIOException failure) {
                return List.of("the cache cannot stand for the reference: " + Row.redacted(failure.getMessage()));
            }
        }

        /**
         * Writes the evidence summary of each decided set, takes away the old summary of a pending one,
         * and then D of every pack, as one change: if the registry refuses any set, the registry and
         * every summary are put back as they were (the old bytes, or no file where there was none), so a
         * rerun starts from the committed tree.
         */
        private static void record(Path repoRoot, List<ReferenceSetVerdict> verdicts, LocalDate day)
                throws IOException {
            Map<Path, Optional<byte[]>> before = new LinkedHashMap<>();
            remember(before, repoRoot.resolve(REGISTRY_FILE));
            for (ReferenceSetVerdict verdict : verdicts) {
                for (Path summary : summariesOf(repoRoot, verdict)) {
                    remember(before, summary);
                }
            }
            Path registry = repoRoot.resolve(REGISTRY_FILE);
            boolean recorded = false;
            try {
                for (ReferenceSetVerdict verdict : verdicts) {
                    RegistryUpdater.record(registry, repoRoot, verdict, day, bundleOf(repoRoot, verdict, day));
                }
                recorded = true;
            } finally {
                if (!recorded) {
                    restore(before);
                }
            }
        }

        private static List<Path> summariesOf(Path repoRoot, ReferenceSetVerdict verdict) {
            Path directory = repoRoot.resolve(SummaryWriter.SET_SUMMARY_DIR);
            return List.of(
                    directory.resolve(SummaryWriter.summaryFileName(verdict.ruleVersion(), JSON)),
                    directory.resolve(SummaryWriter.summaryFileName(verdict.ruleVersion(), MARKDOWN)));
        }

        private static void remember(Map<Path, Optional<byte[]>> before, Path file) throws IOException {
            before.put(file, Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty());
        }

        private static void restore(Map<Path, Optional<byte[]>> before) throws IOException {
            for (Map.Entry<Path, Optional<byte[]>> file : before.entrySet()) {
                if (file.getValue().isPresent()) {
                    Files.write(file.getKey(), file.getValue().get());
                } else {
                    Files.deleteIfExists(file.getKey());
                }
            }
        }

        private static RegistryUpdater.EvidenceBundle bundleOf(
                Path repoRoot, ReferenceSetVerdict verdict, LocalDate day) throws IOException {
            if (verdict.status() == PackVerdict.Status.PENDING) {
                // a set that was decided and is pending now takes its old summary away with D
                for (Path summary : summariesOf(repoRoot, verdict)) {
                    Files.deleteIfExists(summary);
                }
                return RegistryUpdater.EvidenceBundle.none();
            }
            SummaryWriter.WrittenSetSummary written =
                    SummaryWriter.writeSet(repoRoot.resolve(SummaryWriter.SET_SUMMARY_DIR), verdict, day);
            List<RegistryUpdater.CitedFile> cited = new ArrayList<>();
            for (ReferenceSetVerdict.ReferenceOutcome outcome : verdict.references()) {
                cited.add(new RegistryUpdater.CitedFile(
                        ReferencePolicy.manifestPath(outcome.referenceId()), outcome.referenceManifestSha256()));
                cited.add(new RegistryUpdater.CitedFile(outcome.dossierRef(), outcome.dossierSha256()));
            }
            return new RegistryUpdater.EvidenceBundle(
                    SummaryWriter.SET_SUMMARY_DIR + SummaryWriter.summaryFileName(verdict.ruleVersion(), JSON),
                    written.jsonSha256(),
                    verdict.gateSetSha256(),
                    cited);
        }
    }
}
