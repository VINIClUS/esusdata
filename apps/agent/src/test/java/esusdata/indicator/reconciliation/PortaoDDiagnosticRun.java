package esusdata.indicator.reconciliation;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Cell;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Row;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The diagnostic run of the Portão D over every period whose official reference was captured (spec
 * 2026-10-08 §14): for each period the local PEC can execute, every pack C1 to C7 is computed month
 * by month through the reference-scoped cache, classified and compared with <em>each</em> captured
 * revision of its official team export, and the Nota Final is computed over the very same
 * partitions. A period the PEC lacks months of is a row that names them, not an omission.
 *
 * <p>The run decides nothing. Every comparison is a {@link ReferencePurpose#DIAGNOSTIC} one; the
 * revisions are not ranked or filtered ({@code periods} only chooses which captured periods run);
 * the result is a {@link DiagnosticMatrix} that carries no INE; and nothing is written here, nor to
 * a registry. A cell that cannot be computed is an {@link Cell#ERROR} row with its reason, and the
 * others are still computed.
 *
 * <p>The Nota Final of an official export is computed from C1 to C7 of the <em>same export</em>:
 * the manifests of the same period whose {@code raw_sha256} is its own. If one of the seven is
 * missing it is {@link Cell#PENDING} and says so; no other revision is substituted. It reads the
 * partitions the seven packs left in the cache and cannot read the PEC.
 */
final class PortaoDDiagnosticRun {

    private static final Pattern MANIFEST_FILE =
            Pattern.compile("[a-z]{2}-\\d{7}-\\d{4}q[1-3]-(?:c[1-7]|ciii)-team-r([1-9]\\d*)\\.json");
    private static final Pattern REVISION = Pattern.compile("-r(\\d+)$");

    private final ReferenceArtifactStore store;
    private final ReferenceScopedExtracts extracts;
    private final Clock clock;
    private final String municipalityIbge;
    private final Map<Quadrimestre, List<Reference>> byPeriod;

    /** A captured revision of the official reference of one pack, as the run reads it. */
    private record Reference(SiapsReferenceManifest manifest, GatePack pack, Quadrimestre period, String sha256) {

        String id() {
            return manifest.referenceId();
        }
    }

    private PortaoDDiagnosticRun(
            ReferenceArtifactStore store,
            ReferenceScopedExtracts extracts,
            Clock clock,
            String municipalityIbge,
            Map<Quadrimestre, List<Reference>> byPeriod) {
        this.store = store;
        this.extracts = extracts;
        this.clock = clock;
        this.municipalityIbge = municipalityIbge;
        this.byPeriod = byPeriod;
    }

    /**
     * Reads the manifests of {@code manifestsDir}: every {@code .json} file there must be a team
     * reference manifest named by its reference id, and all must be about one municipality.
     *
     * @param requested the periods to run; empty runs every captured period. A requested period no
     *     manifest is about is refused: it would otherwise be a silent omission.
     */
    static PortaoDDiagnosticRun open(
            Path manifestsDir,
            Path artifactDir,
            Set<Quadrimestre> requested,
            ReferenceScopedExtracts extracts,
            Clock clock)
            throws IOException {
        List<Reference> references = new ArrayList<>();
        try (Stream<Path> files = Files.list(manifestsDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                references.add(read(file));
            }
        }
        if (references.isEmpty()) {
            throw new IllegalStateException("the manifests directory has no reference manifest: run the capture first");
        }
        String municipality = references.getFirst().manifest().municipalityIbge();
        if (references.stream()
                .anyMatch(reference -> !municipality.equals(reference.manifest().municipalityIbge()))) {
            throw new IllegalStateException("the manifests are about more than one municipality");
        }
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
        byPeriod.values().forEach(list -> list.sort(order()));
        return new PortaoDDiagnosticRun(
                new ReferenceArtifactStore(artifactDir), extracts, clock, municipality, byPeriod);
    }

    private static Comparator<Reference> order() {
        List<GatePack> packs = GatePack.allWithNotaFinal();
        return Comparator.comparingInt((Reference reference) -> packs.indexOf(reference.pack()))
                .thenComparingInt(reference -> revisionOf(reference.id()));
    }

    private static int revisionOf(String referenceId) {
        Matcher revision = REVISION.matcher(referenceId);
        if (!revision.find()) {
            throw new IllegalArgumentException("not a reference id: " + referenceId);
        }
        return Integer.parseInt(revision.group(1));
    }

    private static Reference read(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (!MANIFEST_FILE.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "a .json file of the manifests directory is not named like a team reference manifest: " + name);
        }
        byte[] bytes = Files.readAllBytes(file);
        SiapsReferenceManifest manifest = SiapsReferenceManifest.fromJson(new String(bytes, StandardCharsets.UTF_8));
        if (!(manifest.referenceId() + ".json").equals(name)) {
            throw new IllegalStateException(name + " is not named by the reference id it holds");
        }
        return new Reference(
                manifest,
                packOf(manifest),
                SiapsFormats.quadrimestre(manifest.quadrimestre()),
                SummaryWriter.sha256(bytes));
    }

    private static GatePack packOf(SiapsReferenceManifest manifest) {
        int code = manifest.indicatorCodes().getFirst();
        return code == GatePack.NOTA_FINAL.siapsCode()
                ? GatePack.NOTA_FINAL
                : GatePack.bySiapsCode(code)
                        .orElseThrow(() -> new IllegalStateException(
                                manifest.referenceId() + " is about no pack the gate compares"));
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
     * Runs every captured period the local source can execute and reports the ones it cannot.
     *
     * @param source the PEC the extracts are read from, proved by the read-only preflight
     * @param localCoverage the months the PEC holds for the municipality
     * @param acquisition what may be read from the PEC for a partition the cache lacks
     * @throws IllegalStateException when the source is another municipality than the manifests'
     */
    DiagnosticMatrix run(SourceIdentity source, Set<YearMonth> localCoverage, AcquisitionInputs acquisition) {
        if (!municipalityIbge.equals(source.municipalityIbge())) {
            throw new IllegalStateException("the manifests are about another municipality than the PEC source");
        }
        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(periods(), localCoverage);
        List<Row> rows = new ArrayList<>();
        for (PeriodExecutionPlan period : plan) {
            rows.addAll(period.runs() ? runPeriod(period.quadrimestre(), source, acquisition) : notRun(period));
        }
        return new DiagnosticMatrix(clock.instant(), source, plan, rows);
    }

    private List<Reference> referencesOf(Quadrimestre period, GatePack pack) {
        return byPeriod.get(period).stream()
                .filter(reference -> reference.pack().equals(pack))
                .toList();
    }

    private List<Row> notRun(PeriodExecutionPlan period) {
        String reason = "meses locais ausentes: " + period.missingMonths();
        List<Row> rows = new ArrayList<>();
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            List<Reference> references = referencesOf(period.quadrimestre(), pack);
            if (references.isEmpty()) {
                rows.add(Row.absent(period.quadrimestre(), pack));
            }
            for (Reference reference : references) {
                rows.add(Row.without(period.quadrimestre(), pack, reference.id(), Cell.MISSING_LOCAL_MONTHS, reason));
            }
        }
        return rows;
    }

    private List<Row> runPeriod(Quadrimestre period, SourceIdentity source, AcquisitionInputs acquisition) {
        List<Row> rows = new ArrayList<>();
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            List<Reference> references = referencesOf(period, pack);
            if (references.isEmpty()) {
                rows.add(Row.absent(period, pack));
            }
            for (Reference reference : references) {
                rows.add(
                        pack.isNotaFinal() ? notaFinalRow(reference, source) : packRow(reference, source, acquisition));
            }
        }
        return rows;
    }

    private Row packRow(Reference reference, SourceIdentity source, AcquisitionInputs acquisition) {
        try {
            IndicatorRule rule = ruleOf(reference.pack());
            ValidatedReference official =
                    ValidatedReference.fromStored(store.load(reference.manifest()), reference.pack());
            QuadrimestreInputs inputs =
                    extracts.loadOrAcquireQuadrimestre(contextOf(reference, rule, source), acquisition);
            LocalClasses local = LocalClasses.of(rule, reference.period(), monthly(inputs, reference.period()));
            PackVerdict verdict = PackVerdict.evaluate(
                    reference.pack(),
                    rule.descriptor().ruleVersion(),
                    ReferencePurpose.DIAGNOSTIC,
                    official,
                    local,
                    inputs.localSourceFingerprint());
            return Row.of(reference.period(), reference.id(), verdict);
        } catch (IOException
                | IllegalStateException
                | IllegalArgumentException
                | UncheckedIOException
                | PecAcquisitionException failure) {
            return Row.failed(reference.period(), reference.pack(), reference.id(), failure);
        }
    }

    private Row notaFinalRow(Reference reference, SourceIdentity source) {
        Map<String, Reference> siblings = siblingsOf(reference);
        if (siblings.size() != GatePack.all().size()) {
            return Row.without(
                    reference.period(),
                    reference.pack(),
                    reference.id(),
                    Cell.PENDING,
                    "faltam as referências do mesmo export oficial para: " + missingPacks(siblings));
        }
        try {
            Map<String, String> shas = new TreeMap<>();
            siblings.forEach((packId, sibling) -> shas.put(packId, sibling.sha256()));
            // Each pack is reduced to its monthly team results before the next is loaded.
            NotaFinalInputs<Map<YearMonth, List<TeamResult>>> inputs = extracts.loadNotaFinal(
                    new NotaFinalContext(municipalityIbge, reference.period(), source, shas),
                    months -> monthly(months, reference.period()));
            ValidatedReference official =
                    ValidatedReference.fromStored(store.load(reference.manifest()), reference.pack());
            PackVerdict verdict = PackVerdict.evaluate(
                    reference.pack(),
                    ComponentIII.RULE_VERSION,
                    ReferencePurpose.DIAGNOSTIC,
                    official,
                    LocalClasses.ofNotaFinal(reference.period(), inputs.byPack()),
                    inputs.localSourceFingerprint());
            return Row.of(reference.period(), reference.id(), verdict);
        } catch (IOException
                | IllegalStateException
                | IllegalArgumentException
                | UncheckedIOException
                | PecAcquisitionException failure) {
            return Row.failed(reference.period(), reference.pack(), reference.id(), failure);
        }
    }

    /** The revision of each of C1 to C7 captured from the same export file as {@code notaFinal}, by pack id. */
    private Map<String, Reference> siblingsOf(Reference notaFinal) {
        Map<String, Reference> siblings = new LinkedHashMap<>();
        for (GatePack pack : GatePack.all()) {
            List<Reference> same = referencesOf(notaFinal.period(), pack).stream()
                    .filter(reference -> reference
                            .manifest()
                            .rawSha256()
                            .equals(notaFinal.manifest().rawSha256()))
                    .toList();
            if (same.size() == 1) {
                siblings.put(pack.packId(), same.getFirst());
            }
        }
        return siblings;
    }

    private static List<String> missingPacks(Map<String, Reference> siblings) {
        return GatePack.all().stream()
                .filter(pack -> !siblings.containsKey(pack.packId()))
                .map(GatePack::code)
                .toList();
    }

    private QuadrimestreContext contextOf(Reference reference, IndicatorRule rule, SourceIdentity source) {
        return new QuadrimestreContext(
                municipalityIbge,
                reference.period(),
                reference.sha256(),
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                source);
    }

    /** The ungated monthly team results of the four months, in the order of the quadrimestre. */
    private static Map<YearMonth, List<TeamResult>> monthly(QuadrimestreInputs inputs, Quadrimestre period) {
        Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
        List<YearMonth> calendar = period.months();
        for (int at = 0; at < calendar.size(); at++) {
            PackInput input = inputs.months().get(at);
            months.put(
                    calendar.get(at),
                    input.rule().evaluate(input.data(), input.context()).teams());
        }
        return months;
    }

    private static IndicatorRule ruleOf(GatePack pack) {
        return IndicatorRuleRegistry.all().stream()
                .filter(rule -> rule.descriptor().id().equals(pack.packId()))
                .findFirst()
                .orElseThrow();
    }
}
