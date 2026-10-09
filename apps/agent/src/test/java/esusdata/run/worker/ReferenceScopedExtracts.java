package esusdata.run.worker;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.result.model.InputFingerprint;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.AcquisitionListener;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.FileExtractStore;
import esusdata.run.extract.ManifestPart;
import esusdata.run.job.CancellationToken;
import esusdata.run.worker.PartitionSidecar.Extract;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.IndividualEncounterModalityContract;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The extracts of the Portão D, kept per reference revision, pack, rule version and source (spec
 * 2026-10-08 §13): a cache of finalized extracts that is deterministic in what it holds and in
 * whether it may be used. Every extract of a month lives in its own partition,
 *
 * <pre>{@code <root>/<municipality>/<quadrimestre>/<reference-sha>/<rule version>/<source key>/<yyyy-MM>/}</pre>
 *
 * with the extract files, the manifests (for C1 the extract and its {@code -team} supplement, which
 * are one unit, ADR 0033) and the {@value PartitionSidecar#FILE} that says what the partition was
 * acquired for.
 *
 * <p><b>A partition is used only for what it was acquired for, checked against the expected
 * context and never against its own manifests.</b> The sidecar must name the same municipality,
 * quadrimestre, reference, pack, rule version, month and source as the context; the manifests must
 * be those the sidecar lists; and the read plan of the rule, built from the context, must accept
 * them ({@code ReadPlan.requireCovers}: source, municipality, period and, in v2, every part's
 * version, query checksum, window and binds). C1's v1 extract must carry the version and query
 * checksum the execution plane writes for it. Any mismatch is a refusal. A partition that exists
 * and fails is never read, completed or replaced: the cache does not silently acquire over it.
 *
 * <p>Acquisition goes through the very command {@code RunExecutor.runLive} builds from the same
 * {@code ReadPlan}, into a staging directory beside the partition that becomes the partition in one
 * atomic move, after passing the same validation: a crash leaves no half partition.
 */
public final class ReferenceScopedExtracts {

    private static final String SOURCE_ZONE = "America/Sao_Paulo";
    private static final String LABEL = "portao-d";
    private static final String STAGING = ".staging-";
    private static final String EXTRACT = "extract/";
    private static final String SUPPLEMENT = "supplement/";
    private static final Pattern IBGE = Pattern.compile("\\d{7}");

    private final Path root;
    private final Clock clock;

    /**
     * The inputs of the four months of a quadrimestre, in order.
     *
     * @param months one pack input per month
     * @param localSourceFingerprint {@code InputFingerprint} of the identity and of every extract's
     *     checksum, query checksum and adapter version (the C1 supplement included); the same value
     *     when it is computed again from the persisted partitions
     */
    public record QuadrimestreInputs(List<PackInput> months, String localSourceFingerprint) {

        public QuadrimestreInputs {
            months = List.copyOf(months);
            Objects.requireNonNull(localSourceFingerprint, "localSourceFingerprint");
        }
    }

    /**
     * What the Nota Final reads: the partitions of C1 to C7 of one municipality and quadrimestre,
     * each of the reference revision named here.
     *
     * @param municipalityIbge the municipality
     * @param quadrimestre the quadrimestre
     * @param sourceIdentity the PEC the partitions were read from
     * @param referenceManifestSha256ByPack the manifest hash of the reference revision of each of the
     *     seven packs (the siblings of the Nota Final's reference), by pack id
     */
    public record NotaFinalContext(
            String municipalityIbge,
            Quadrimestre quadrimestre,
            SourceIdentity sourceIdentity,
            Map<String, String> referenceManifestSha256ByPack) {

        public NotaFinalContext {
            if (municipalityIbge == null || !IBGE.matcher(municipalityIbge).matches()) {
                throw new IllegalArgumentException("municipalityIbge must be a 7-digit IBGE code");
            }
            Objects.requireNonNull(quadrimestre, "quadrimestre");
            Objects.requireNonNull(sourceIdentity, "sourceIdentity");
            referenceManifestSha256ByPack = Map.copyOf(referenceManifestSha256ByPack);
            Set<String> packs = IndicatorRuleRegistry.all().stream()
                    .map(rule -> rule.descriptor().id())
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!packs.equals(new TreeSet<>(referenceManifestSha256ByPack.keySet()))) {
                throw new IllegalArgumentException(
                        "the Nota Final reads exactly the packs " + packs + ", one reference each");
            }
        }
    }

    /**
     * The inputs of the Nota Final.
     *
     * @param byPack what was kept of the four months of each of C1 to C7, by pack id
     * @param localSourceFingerprint {@code InputFingerprint} of the seven pack fingerprints
     * @param <T> what is kept of one pack's four months
     */
    public record NotaFinalInputs<T>(Map<String, T> byPack, String localSourceFingerprint) {

        public NotaFinalInputs {
            byPack = Map.copyOf(byPack);
            Objects.requireNonNull(localSourceFingerprint, "localSourceFingerprint");
        }
    }

    /** One month as it was loaded: the input of the pack and the manifests it was built from. */
    private record Loaded(
            YearMonth month, PackInput input, ExtractionManifest primary, ExtractionManifest supplement) {}

    /**
     * @param root the directory of the cache; created when something is acquired
     * @param clock the clock the sidecar's {@code acquired_at} is read from
     */
    public ReferenceScopedExtracts(Path root, Clock clock) {
        this.root = Objects.requireNonNull(root, "root");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The id the extract of a rule and month is published under in its partition. */
    public static String extractionId(IndicatorRule rule, YearMonth month) {
        return LABEL + "-" + rule.descriptor().code().toLowerCase(Locale.ROOT) + "-" + month;
    }

    /** The directory of the partition of {@code context}, whether or not it exists. */
    public Path partitionOf(ReferenceExecutionContext context) {
        return root.resolve(context.municipalityIbge())
                .resolve(context.quadrimestreName())
                .resolve(context.referenceManifestSha256())
                .resolve(context.ruleVersion())
                .resolve(context.sourceIdentity().key())
                .resolve(context.month().toString());
    }

    /**
     * The input of one month: loaded when its partition exists and is the one asked for, acquired
     * when there is none and {@code acquisition} allows it.
     *
     * @throws IllegalStateException if the partition exists but is not what {@code context} expects,
     *     or does not exist and nothing may be acquired
     */
    public PackInput loadOrAcquire(ReferenceExecutionContext context, AcquisitionInputs acquisition)
            throws IOException {
        return loaded(context, acquisition).input();
    }

    /**
     * The four months of a quadrimestre, each loaded or acquired as {@link #loadOrAcquire}, and the
     * fingerprint of the local source they make together.
     */
    public QuadrimestreInputs loadOrAcquireQuadrimestre(QuadrimestreContext context, AcquisitionInputs acquisition)
            throws IOException {
        List<Loaded> months = new ArrayList<>();
        for (YearMonth month : context.quadrimestre().months()) {
            months.add(loaded(context.at(month), acquisition));
        }
        return new QuadrimestreInputs(
                months.stream().map(Loaded::input).toList(), fingerprint(context.sourceIdentity(), months));
    }

    /**
     * The inputs of the Nota Final: the partitions of C1 to C7 that exist for the references of
     * {@code context}. It takes no {@link AcquisitionInputs}, so it cannot read the PEC: a partition
     * that is missing or does not match is a refusal.
     *
     * <p>Each pack's four months go to {@code reduce} as soon as they are loaded, and only what it
     * returns is kept: the datasets of the seven packs are never in memory together, which a real
     * quadrimestre does not fit in, and the Nota Final reads only the monthly team results of each.
     *
     * @param reduce what to keep of one pack's four months; it must not keep the inputs themselves
     */
    public <T> NotaFinalInputs<T> loadNotaFinal(NotaFinalContext context, Function<QuadrimestreInputs, T> reduce)
            throws IOException {
        Map<String, T> byPack = new LinkedHashMap<>();
        Map<String, String> fingerprints = new TreeMap<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            String pack = rule.descriptor().id();
            QuadrimestreInputs inputs = loadOrAcquireQuadrimestre(
                    new QuadrimestreContext(
                            context.municipalityIbge(),
                            context.quadrimestre(),
                            context.referenceManifestSha256ByPack().get(pack),
                            pack,
                            rule.descriptor().ruleVersion(),
                            context.sourceIdentity()),
                    AcquisitionInputs.none());
            byPack.put(pack, reduce.apply(inputs));
            fingerprints.put(pack, inputs.localSourceFingerprint());
        }
        return new NotaFinalInputs<>(byPack, InputFingerprint.compute(fingerprints));
    }

    private Loaded loaded(ReferenceExecutionContext context, AcquisitionInputs acquisition) throws IOException {
        Path partition = partitionOf(context);
        if (Files.exists(partition)) {
            return load(context, partition);
        }
        if (acquisition instanceof AcquisitionInputs.Live live) {
            return acquire(context, partition, live);
        }
        throw new IllegalStateException(
                "no extracts of " + describe(context) + ", and nothing may be acquired for them");
    }

    // ---- loading: the partition against the expected context

    private static Loaded load(ReferenceExecutionContext context, Path partition) throws IOException {
        IndicatorRule rule = IndicatorRuleRegistry.require(context.packId(), context.ruleVersion());
        ReadPlan plan = ReadPlan.of(rule, context.month(), CapabilityCatalog.packaged());
        PartitionSidecar sidecar = readSidecar(context, partition);
        List<String> differences = sidecar.differencesFrom(context);
        if (!differences.isEmpty()) {
            throw refusal(context, "the sidecar is of another partition: " + differences + " differ");
        }
        FileExtractStore store = new FileExtractStore(partition);
        String id = extractionId(rule, context.month());
        ExtractionManifest primary = store.readManifest(id);
        ExtractionManifest supplement =
                plan.hasSupplement() ? store.readManifest(ReadPlan.supplementExtractionId(id)) : null;
        requireListedBySidecar(context, sidecar, primary, supplement);
        requireExpected(context, plan, id, primary, supplement);
        CanonicalDataset data = supplement == null ? plan.read(store, primary) : plan.read(store, primary, supplement);
        EvaluationContext evaluation = EvaluationContext.endOfMonth(context.municipalityIbge(), context.month());
        return new Loaded(context.month(), new PackInput(rule, data, evaluation), primary, supplement);
    }

    private static PartitionSidecar readSidecar(ReferenceExecutionContext context, Path partition) throws IOException {
        Path file = partition.resolve(PartitionSidecar.FILE);
        if (!Files.isRegularFile(file)) {
            throw refusal(context, "the partition has no " + PartitionSidecar.FILE + ": it was not acquired here");
        }
        try {
            return PartitionSidecar.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException notTheSidecar) {
            throw refusal(context, PartitionSidecar.FILE + " is unreadable", notTheSidecar);
        }
    }

    /** The manifests on disk are the extracts the sidecar lists, with the identity it recorded for each. */
    private static void requireListedBySidecar(
            ReferenceExecutionContext context,
            PartitionSidecar sidecar,
            ExtractionManifest primary,
            ExtractionManifest supplement) {
        List<Extract> onDisk = new ArrayList<>();
        onDisk.add(extractOf(primary));
        if (supplement != null) {
            onDisk.add(extractOf(supplement));
        }
        if (!onDisk.equals(sidecar.extracts())) {
            throw refusal(
                    context,
                    "the extracts differ from those the sidecar lists (id, checksum, query checksum or adapter version)");
        }
    }

    private static Extract extractOf(ExtractionManifest manifest) {
        return new Extract(
                manifest.extractionId(), manifest.checksum(), manifest.queryChecksum(), manifest.adapterVersion());
    }

    /**
     * The read plan of the rule, built from the expected context, accepts the extracts: the run
     * context below is made of the expected source, municipality and period, not of the manifest's.
     */
    private static void requireExpected(
            ReferenceExecutionContext context,
            ReadPlan plan,
            String id,
            ExtractionManifest primary,
            ExtractionManifest supplement) {
        RunExecutor.RunContext expected = new RunExecutor.RunContext(
                LABEL,
                LABEL,
                context.sourceIdentity().pecSourceId(),
                1,
                LABEL,
                id,
                context.municipalityIbge(),
                context.month().toString(),
                context.packId(),
                context.ruleVersion(),
                LABEL);
        try {
            plan.requireCovers(primary, expected);
            if (supplement != null) {
                plan.requireSupplementCovers(primary, supplement);
            }
        } catch (IllegalStateException notTheExpected) {
            throw refusal(context, notTheExpected.getMessage(), notTheExpected);
        }
        if (!plan.isCanonicalV2()) {
            requireAsTheExecutionPlaneWritesV1(context, primary);
        }
    }

    /**
     * C1's v1 extract has no parts for the plan to check, so what the execution plane writes for it
     * is the expectation: the adapter version and the query checksum of its frozen contract.
     */
    private static void requireAsTheExecutionPlaneWritesV1(ReferenceExecutionContext context, ExtractionManifest v1) {
        if (!IndividualEncounterModalityContract.ADAPTER_VERSION.equals(v1.adapterVersion())
                || !IndividualEncounterModalityContract.QUERY_CHECKSUM.equals(v1.queryChecksum())) {
            throw refusal(context, "the v1 extract was not read with the adapter version and query of this release");
        }
    }

    // ---- acquiring: into a staging directory, validated, then moved into place

    private Loaded acquire(ReferenceExecutionContext context, Path partition, AcquisitionInputs.Live live)
            throws IOException {
        requireSameSource(context, live);
        IndicatorRule rule = IndicatorRuleRegistry.require(context.packId(), context.ruleVersion());
        ReadPlan plan = ReadPlan.of(rule, context.month(), CapabilityCatalog.packaged());
        Files.createDirectories(partition.getParent());
        Path staging = Files.createTempDirectory(partition.getParent(), STAGING + context.month() + "-");
        boolean published = false;
        try {
            String id = extractionId(rule, context.month());
            readInto(staging, id, plan, live);
            writeSidecar(staging, context, plan, id);
            load(context, staging);
            Files.move(staging, partition, StandardCopyOption.ATOMIC_MOVE);
            published = true;
        } finally {
            if (!published) {
                discard(staging);
            }
        }
        return load(context, partition);
    }

    /** The partition is keyed by a source identity: the PEC about to be read must be that one. */
    private static void requireSameSource(ReferenceExecutionContext context, AcquisitionInputs.Live live) {
        PecSourceIdentity identity = live.identity();
        SourceIdentity wanted = context.sourceIdentity();
        if (!identity.sourceId().equals(wanted.pecSourceId())
                || !identity.pecVersion().equals(wanted.pecVersion())
                || !identity.readModel().equals(wanted.readModel())
                || !live.connection().municipalityIbge().equals(wanted.municipalityIbge())) {
            throw refusal(context, "the acquisition inputs are not those of the source identity it is keyed by");
        }
    }

    /** The same commands {@code RunExecutor.runLive} builds from the plan: the supplement first, then the extract. */
    private static void readInto(Path directory, String id, ReadPlan plan, AcquisitionInputs.Live live) {
        Acquisition adapter = live.adapters().into(directory);
        PecConnectionProperties connection = live.connection();
        if (plan.hasSupplement()) {
            adapter.acquire(
                    plan.supplementCommand(connection, live.identity(), id, SOURCE_ZONE),
                    new CancellationToken(),
                    new Silent());
        }
        adapter.acquire(
                plan.command(connection, live.identity(), id, SOURCE_ZONE), new CancellationToken(), new Silent());
    }

    private void writeSidecar(Path directory, ReferenceExecutionContext context, ReadPlan plan, String id)
            throws IOException {
        FileExtractStore store = new FileExtractStore(directory);
        List<Extract> extracts = new ArrayList<>();
        extracts.add(extractOf(store.readManifest(id)));
        if (plan.hasSupplement()) {
            extracts.add(extractOf(store.readManifest(ReadPlan.supplementExtractionId(id))));
        }
        PartitionSidecar sidecar = PartitionSidecar.of(context, clock.instant(), extracts);
        Files.writeString(directory.resolve(PartitionSidecar.FILE), sidecar.toJson(), StandardCharsets.UTF_8);
    }

    /** Removes what is left of a staging directory whose move did not happen. */
    private static void discard(Path staging) {
        if (!Files.exists(staging)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException leftover) {
            // best effort: nothing reads a ".staging-" directory, so what stays behind is harmless
        }
    }

    // ---- the fingerprint of the local source

    private static String fingerprint(SourceIdentity identity, List<Loaded> months) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("identity/pec_source_id", identity.pecSourceId());
        fields.put("identity/pec_version", identity.pecVersion());
        fields.put("identity/postgres_version", identity.postgresVersion());
        fields.put("identity/municipality_ibge", identity.municipalityIbge());
        fields.put("identity/read_model", identity.readModel());
        for (Loaded month : months) {
            addExtract(fields, month.month() + "/" + EXTRACT, month.primary());
            if (month.supplement() != null) {
                addExtract(fields, month.month() + "/" + SUPPLEMENT, month.supplement());
            }
        }
        return InputFingerprint.compute(fields);
    }

    private static void addExtract(Map<String, String> fields, String prefix, ExtractionManifest manifest) {
        fields.put(prefix + "checksum", manifest.checksum());
        fields.put(prefix + "query_checksum", manifest.queryChecksum());
        fields.put(prefix + "adapter_version", manifest.adapterVersion());
        for (ManifestPart part : manifest.parts()) {
            fields.put(
                    prefix + "part/" + part.index() + "/" + part.capability(),
                    part.adapterVersion() + ":" + part.queryChecksum());
        }
    }

    // ---- messages

    private static IllegalStateException refusal(ReferenceExecutionContext context, String detail) {
        return new IllegalStateException("extracts of " + describe(context) + " refused: " + detail);
    }

    private static IllegalStateException refusal(ReferenceExecutionContext context, String detail, Throwable cause) {
        return new IllegalStateException("extracts of " + describe(context) + " refused: " + detail, cause);
    }

    /** Where the extracts are from, for a message: no source detail, nothing of the data. */
    private static String describe(ReferenceExecutionContext context) {
        return context.ruleVersion() + " " + context.month() + " of reference "
                + context.referenceManifestSha256().substring(0, 12);
    }

    /** Nothing is logged: the progress and uncertainty of a read carry no data and need no echo. */
    private static final class Silent implements AcquisitionListener {

        @Override
        public void onProgress() {
            // not reported
        }

        @Override
        public void onUncertainOutcome(String reason) {
            // the harness reads, it does not guard the source; the caller stops on the exception
        }
    }
}
