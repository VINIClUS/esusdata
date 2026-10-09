package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.result.model.InputFingerprint;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionFilePaths;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.FileExtractStore;
import esusdata.run.extract.ManifestChecksums;
import esusdata.run.extract.ManifestPart;
import esusdata.run.worker.PartitionSidecar.Extract;
import esusdata.run.worker.ReferenceScopedExtracts.NotaFinalContext;
import esusdata.run.worker.ReferenceScopedExtracts.NotaFinalInputs;
import esusdata.run.worker.ReferenceScopedExtracts.QuadrimestreInputs;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The extract cache of the Portão D: an extract is used only for the municipality, quadrimestre,
 * reference revision, rule version, source and month it was acquired for, and is checked against the
 * expected context rather than against its own manifest. A fixture PEC ({@link FixturePec}) stands
 * in for the source, so what the cache asks of it is visible: the commands of the read plan when it
 * may acquire, and nothing at all when it may not.
 */
class ReferenceScopedExtractsTest {

    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String OTHER_IBGE = "3550308";
    private static final Quadrimestre FIRST = new Quadrimestre(2026, 1);
    private static final YearMonth JANUARY = YearMonth.of(2026, 1);
    private static final YearMonth FEBRUARY = YearMonth.of(2026, 2);
    private static final String OTHER_REFERENCE = "ef".repeat(32);
    private static final String ZONE = "America/Sao_Paulo";
    private static final String C7_JANUARY = "portao-d-c7-2026-01";
    private static final String C1_JANUARY = "portao-d-c1-2026-01";
    private static final String C1_TEAM_JANUARY = C1_JANUARY + "-team";
    private static final String MANIFEST_SUFFIX = ".manifest.json";
    private static final String NOTHING_MAY_BE_ACQUIRED = "nothing may be acquired";
    private static final String COVERS = "covers source";
    private static final String OTHER_SOURCE = "pec-other";
    private static final String OTHER_POSTGRES = "15.4";
    private static final String OTHER_PEC_VERSION = "5.5.27";
    private static final String OLDER_ADAPTER = "0.0.1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path root;

    private final FixturePec pec = new FixturePec(IBGE);

    // ---- acquiring and loading

    @Test
    void aMonthIsAcquiredThroughTheCommandOfItsReadPlanAndAfterwardsOnlyLoaded() throws Exception {
        IndicatorRule rule = new C7Pack();
        ReferenceExecutionContext context = context(rule, JANUARY, pec);

        PackInput acquired = cache().loadOrAcquire(context, pec.inputs());
        PackInput loaded = cache().loadOrAcquire(context, AcquisitionInputs.none());

        ReadPlan plan = ReadPlan.of(rule, JANUARY, CapabilityCatalog.packaged());
        assertThat(pec.requests())
                .as("one command, the one the run pipeline builds from the plan, and none for the reload")
                .containsExactly(plan.command(pec.connection(), pec.pecIdentity(), C7_JANUARY, ZONE));
        assertThat(loaded.rule().descriptor().id()).isEqualTo(C7Pack.ID);
        assertThat(loaded.context()).isEqualTo(acquired.context());
        assertThat(loaded.context().competencia()).isEqualTo(JANUARY);
        assertThat(loaded.context().municipalityIbge()).isEqualTo(IBGE);
    }

    @Test
    void aPartitionIsKeyedByMunicipalityQuadrimestreReferenceRuleVersionAndSource() throws Exception {
        IndicatorRule rule = new C7Pack();
        ReferenceExecutionContext context = context(rule, JANUARY, pec);

        cache().loadOrAcquire(context, pec.inputs());

        Path partition = root.resolve(IBGE)
                .resolve("2026Q1")
                .resolve(referenceOf(rule))
                .resolve(rule.descriptor().ruleVersion())
                .resolve(pec.sourceIdentity().key())
                .resolve("2026-01");
        assertThat(cache().partitionOf(context)).isEqualTo(partition);
        assertThat(partition.resolve(C7_JANUARY + MANIFEST_SUFFIX)).isRegularFile();
        assertThat(ExtractionFilePaths.dataFile(partition, C7_JANUARY)).isRegularFile();
        assertThat(partition.resolve(PartitionSidecar.FILE)).isRegularFile();
    }

    @Test
    void c1IsAcquiredAsAPairTheTeamSupplementFirstAndRunsWithIt() throws Exception {
        IndicatorRule rule = new C1Pack();
        ReferenceExecutionContext context = context(rule, JANUARY, pec);

        PackInput input = cache().loadOrAcquire(context, pec.inputs());

        ReadPlan plan = ReadPlan.of(rule, JANUARY, CapabilityCatalog.packaged());
        assertThat(pec.requests())
                .containsExactly(
                        plan.supplementCommand(pec.connection(), pec.pecIdentity(), C1_JANUARY, ZONE),
                        plan.command(pec.connection(), pec.pecIdentity(), C1_JANUARY, ZONE));
        assertThat(sidecarOf(context).extracts())
                .extracting(Extract::extractionId)
                .containsExactly(C1_JANUARY, C1_TEAM_JANUARY);
        RuleOutcome outcome = input.rule().evaluate(input.data(), input.context());
        assertThat(outcome.result().numerator().intValue()).isEqualTo(FixturePec.PROGRAMADO);
        assertThat(outcome.result().denominator().intValue()).isEqualTo(FixturePec.PROGRAMADO + FixturePec.ESPONTANEO);
    }

    @Test
    void theSidecarSaysWhatThePartitionWasAcquiredForAndWhichExtractsItHolds() throws Exception {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        FileExtractStore store = new FileExtractStore(cache().partitionOf(context));

        String text = sidecarText(context);

        assertThat(PartitionSidecar.fromJson(text))
                .isEqualTo(PartitionSidecar.of(
                        context,
                        CLOCK.instant(),
                        List.of(
                                extractOf(store.readManifest(C1_JANUARY)),
                                extractOf(store.readManifest(C1_TEAM_JANUARY)))));
        assertThat(text).endsWith("}\n").doesNotContain("\r").contains("\n  \"pack\": \"c1-mais-acesso\"");
    }

    @Test
    void anotherReferenceRevisionIsAnotherPartitionAndTheFirstIsLeftAlone() throws Exception {
        ReferenceExecutionContext first = context(new C7Pack(), JANUARY, pec);
        ReferenceExecutionContext second = ofReference(first, OTHER_REFERENCE);
        ReferenceScopedExtracts cache = cache();
        cache.loadOrAcquire(first, pec.inputs());
        String before = sidecarText(first);
        AcquisitionInputs none = AcquisitionInputs.none();

        assertThatThrownBy(() -> cache.loadOrAcquire(second, none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOTHING_MAY_BE_ACQUIRED);
        cache.loadOrAcquire(second, pec.inputs());

        assertThat(cache.partitionOf(second)).isNotEqualTo(cache.partitionOf(first));
        assertThat(sidecarFile(second)).isRegularFile();
        assertThat(sidecarText(first)).isEqualTo(before);
        assertThat(pec.requests()).as("one read per partition").hasSize(2);
    }

    // ---- a partition is used only for what it was acquired for

    static Stream<Arguments> partitionsOfSomethingElse() {
        ReferenceExecutionContext base = context(new C7Pack(), JANUARY, new FixturePec(IBGE));
        return Stream.of(
                arguments("municipality_ibge", inMunicipality(base, new FixturePec(OTHER_IBGE).sourceIdentity())),
                arguments("reference_manifest_sha256", ofReference(base, OTHER_REFERENCE)),
                arguments("rule_version", ofRuleVersion(base, base.packId() + "@0.0.1")),
                arguments("month", inMonth(base, FEBRUARY)),
                arguments(
                        "identity.postgres_version",
                        readFrom(base, new FixturePec(IBGE).sourceIdentity(OTHER_POSTGRES))),
                arguments("identity.pec_version", readFrom(base, withPecVersion(base.sourceIdentity()))),
                arguments("identity.pec_source_id", readFrom(base, withSourceId(base.sourceIdentity()))),
                arguments("identity.read_model", readFrom(base, withReadModel(base.sourceIdentity()))));
    }

    @ParameterizedTest(name = "acquired for another {0}")
    @MethodSource("partitionsOfSomethingElse")
    void aPartitionAcquiredForSomethingElseIsRefusedAndNeverReplaced(
            String difference, ReferenceExecutionContext acquiredFor) throws Exception {
        ReferenceExecutionContext asked = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(asked, pec.inputs());
        List<Extract> extracts = sidecarOf(asked).extracts();
        Files.writeString(
                sidecarFile(asked),
                PartitionSidecar.of(acquiredFor, CLOCK.instant(), extracts).toJson());

        assertRefused(asked, difference);
    }

    @Test
    void aPartitionWithoutItsSidecarWasNotAcquiredHereAndIsRefused() throws Exception {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        Files.delete(sidecarFile(context));

        assertRefused(context, PartitionSidecar.FILE);
    }

    @Test
    void aSidecarThatIsNotTheOneTheCacheWroteIsRefused() throws Exception {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        Files.writeString(sidecarFile(context), sidecarText(context).replace("\"schema_version\": \"1\"", "\"x\": 1"));

        assertRefused(context, "is unreadable");
    }

    @Test
    void aManifestOtherThanTheOneTheSidecarListsIsRefused() throws Exception {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        rewrite(context, C7_JANUARY, manifest -> manifest.put("checksum", "0".repeat(64)));

        assertRefused(context, "differ from those the sidecar lists");
    }

    // ---- an extract is checked against the expected context, not against its own manifest

    static Stream<Arguments> manifestsOfSomethingElse() {
        return Stream.of(arguments("municipalityIbge", OTHER_IBGE), arguments("sourceId", OTHER_SOURCE));
    }

    @ParameterizedTest(name = "an extract of another {0}")
    @MethodSource("manifestsOfSomethingElse")
    void anExtractOfAnotherMunicipalityOrSourceIsRefusedEvenWhenTheSidecarAgreesWithIt(String property, String value)
            throws Exception {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        rewrite(context, C7_JANUARY, manifest -> manifest.put(property, value));
        resign(context, C7_JANUARY);

        assertRefused(context, COVERS);
    }

    @Test
    void aV2ExtractReadWithAnotherAdapterVersionIsRefusedEvenWhenTheSidecarAgreesWithIt() throws Exception {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        readFirstPartWithAnOlderAdapter(context, C7_JANUARY);
        resign(context, C7_JANUARY);

        assertRefused(context, "was not read as");
    }

    static Stream<Arguments> v1ExtractsOfAnotherRelease() {
        return Stream.of(
                arguments("adapterVersion", OLDER_ADAPTER), arguments("queryChecksum", "sha256:" + "0".repeat(64)));
    }

    @ParameterizedTest(name = "a v1 extract with another {0}")
    @MethodSource("v1ExtractsOfAnotherRelease")
    void c1sV1ExtractMustCarryTheAdapterVersionAndQueryChecksumOfThisRelease(String property, String value)
            throws Exception {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        rewrite(context, C1_JANUARY, manifest -> manifest.put(property, value));
        resign(context, C1_JANUARY, C1_TEAM_JANUARY);

        assertRefused(context, "adapter version and query");
    }

    @Test
    void c1WithoutItsTeamSupplementIsRefused() throws Exception {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        Files.delete(cache().partitionOf(context).resolve(C1_TEAM_JANUARY + MANIFEST_SUFFIX));

        assertRefused(context, C1_TEAM_JANUARY);
    }

    @Test
    void c1WithATeamSupplementOfAnotherWindowIsRefusedEvenWhenTheSidecarAgreesWithIt() throws Exception {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        rewrite(
                context,
                C1_TEAM_JANUARY,
                manifest -> manifest.put(
                        "periodEndExclusive",
                        LocalDate.parse(manifest.path("periodEndExclusive").stringValue())
                                .plusDays(1)
                                .toString()));
        resign(context, C1_JANUARY, C1_TEAM_JANUARY);

        assertRefused(context, "supplementary extract");
    }

    @Test
    void c1WithATamperedTeamSupplementIsRefusedAlthoughItsOwnExtractIsIntact() throws Exception {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        cache().loadOrAcquire(context, pec.inputs());
        emptyGzip(ExtractionFilePaths.dataFile(cache().partitionOf(context), C1_TEAM_JANUARY));

        assertRefused(context, "Checksum mismatch");
    }

    // ---- an acquisition that is not what was asked for publishes nothing

    static Stream<Arguments> pecsThatWriteWhatWasNotAskedFor() {
        return Stream.of(
                arguments("municipality", new C7Pack(), new FixturePec(IBGE).writingMunicipality(OTHER_IBGE), COVERS),
                arguments("source", new C7Pack(), new FixturePec(IBGE).writingSource(OTHER_SOURCE), COVERS),
                arguments("month", new C7Pack(), new FixturePec(IBGE).writingMonthsOffBy(1), COVERS),
                arguments("v1 month", new C1Pack(), new FixturePec(IBGE).writingMonthsOffBy(1), COVERS),
                arguments(
                        "v1 adapter version",
                        new C1Pack(),
                        new FixturePec(IBGE).writingV1AdapterVersion(ExtractFixtures.ADAPTER_VERSION),
                        "adapter version and query"));
    }

    @ParameterizedTest(name = "a PEC that writes another {0}")
    @MethodSource("pecsThatWriteWhatWasNotAskedFor")
    void anAcquisitionThatIsNotWhatWasAskedForIsRefusedAndLeavesNothingBehind(
            String difference, IndicatorRule rule, FixturePec misbehaving, String reason) {
        ReferenceExecutionContext context = context(rule, JANUARY, misbehaving);
        ReferenceScopedExtracts cache = cache();
        AcquisitionInputs live = misbehaving.inputs();

        assertThatThrownBy(() -> cache.loadOrAcquire(context, live))
                .as(difference)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(reason);

        assertThat(cache.partitionOf(context)).doesNotExist();
        assertThat(cache.partitionOf(context).getParent()).isEmptyDirectory();
    }

    @Test
    void anAcquisitionThatFailsHalfwayLeavesNeitherAPartitionNorItsStagingDirectory() {
        ReferenceExecutionContext context = context(new C1Pack(), JANUARY, pec);
        ReferenceScopedExtracts cache = cache();
        AcquisitionInputs dyingAfterTheSupplement =
                AcquisitionInputs.of(pec.connection(), pec.pecIdentity(), directory -> {
                    Acquisition honest = pec.acquisitionInto(directory);
                    return (command, cancellation, listener) -> {
                        if (command.extractionId().endsWith("-team")) {
                            return honest.acquire(command, cancellation, listener);
                        }
                        throw new IllegalStateException("the connection was lost");
                    };
                });

        assertThatThrownBy(() -> cache.loadOrAcquire(context, dyingAfterTheSupplement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the connection was lost");

        assertThat(pec.requests()).as("the supplement was read first").hasSize(1);
        assertThat(cache.partitionOf(context)).doesNotExist();
        assertThat(cache.partitionOf(context).getParent()).isEmptyDirectory();
    }

    @Test
    void theSourceTheCacheMayReadMustBeTheOneItsPartitionIsKeyedBy() {
        ReferenceExecutionContext context = context(new C7Pack(), JANUARY, pec);
        ReferenceScopedExtracts cache = cache();
        AcquisitionInputs another = AcquisitionInputs.of(
                pec.connection(),
                new PecSourceIdentity(
                        FixturePec.SOURCE_ID, OTHER_PEC_VERSION, AcquisitionInputs.READ_MODEL, AcquisitionInputs.ROLE),
                pec::acquisitionInto);

        assertThatThrownBy(() -> cache.loadOrAcquire(context, another))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source identity");

        assertThat(pec.requests()).isEmpty();
    }

    // ---- the quadrimestre and the fingerprint of the local source

    @Test
    void aQuadrimestreIsFourMonthsAndItsFingerprintIsTheSameWhenComputedAgainFromThePartitions() throws Exception {
        QuadrimestreContext context = quadrimestre(new C7Pack(), pec);

        QuadrimestreInputs acquired = cache().loadOrAcquireQuadrimestre(context, pec.inputs());
        QuadrimestreInputs reloaded = cache().loadOrAcquireQuadrimestre(context, AcquisitionInputs.none());

        assertThat(acquired.months())
                .extracting(month -> month.context().competencia())
                .containsExactlyElementsOf(FIRST.months());
        assertThat(pec.requests()).as("four reads, none for the reload").hasSize(4);
        assertThat(acquired.localSourceFingerprint()).startsWith("sha256:");
        assertThat(reloaded.localSourceFingerprint()).isEqualTo(acquired.localSourceFingerprint());
    }

    @Test
    void theFingerprintCoversTheSourceTheEncountersAndTheTeamSupplement() throws Exception {
        QuadrimestreContext context = quadrimestre(new C1Pack(), pec);
        QuadrimestreContext otherSource = quadrimestre(new C1Pack(), pec, pec.sourceIdentity(OTHER_POSTGRES));

        String base = fingerprintOf("base", context, pec);

        assertThat(fingerprintOf("same", context, new FixturePec(IBGE))).isEqualTo(base);
        assertThat(List.of(
                        base,
                        fingerprintOf("encounters", context, new FixturePec(IBGE).encounters(7, 2)),
                        fingerprintOf("team", context, new FixturePec(IBGE).teamIne("0000000019")),
                        fingerprintOf("source", otherSource, pec)))
                .as("the extracts, the supplement and the source each change it")
                .doesNotHaveDuplicates();
    }

    // ---- the Nota Final never reads the source

    @Test
    void theNotaFinalReadsThePartitionsOfC1ToC7AndItsFingerprintIsThatOfTheirFingerprints() throws Exception {
        Map<String, String> fingerprints = acquireAll(pec);
        int requests = pec.requests().size();

        NotaFinalInputs<Integer> inputs =
                cache().loadNotaFinal(notaFinal(pec), pack -> pack.months().size());

        assertThat(inputs.byPack().keySet()).isEqualTo(fingerprints.keySet());
        assertThat(inputs.byPack().values()).containsOnly(4);
        assertThat(inputs.localSourceFingerprint()).isEqualTo(InputFingerprint.compute(fingerprints));
        assertThat(pec.requests()).as("the Nota Final reads no source").hasSize(requests);
    }

    @Test
    void eachPackOfTheNotaFinalIsKeptOnlyAsWhatItWasReducedToInTheOrderOfTheRegistry() throws Exception {
        acquireAll(pec);
        List<String> reduced = new ArrayList<>();

        NotaFinalInputs<String> inputs = cache().loadNotaFinal(notaFinal(pec), pack -> {
            String id = pack.months().getFirst().rule().descriptor().id();
            reduced.add(id);
            return id;
        });

        assertThat(reduced)
                .containsExactlyElementsOf(IndicatorRuleRegistry.all().stream()
                        .map(rule -> rule.descriptor().id())
                        .toList());
        assertThat(inputs.byPack()).allSatisfy((pack, kept) -> assertThat(kept).isEqualTo(pack));
    }

    @Test
    void aMissingPartitionIsARefusalOfTheNotaFinalNotAnAcquisition() throws Exception {
        acquireAll(pec);
        ReferenceExecutionContext c3March = context(new C3Pack(), YearMonth.of(2026, 3), pec);
        deleteTree(cache().partitionOf(c3March));
        int requests = pec.requests().size();
        ReferenceScopedExtracts cache = cache();
        NotaFinalContext context = notaFinal(pec);

        assertThatThrownBy(() -> cache.loadNotaFinal(context, Function.identity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(C3Pack.ID)
                .hasMessageContaining(NOTHING_MAY_BE_ACQUIRED);

        assertThat(pec.requests()).hasSize(requests);
        assertThat(cache.partitionOf(c3March)).doesNotExist();
    }

    @Test
    void theNotaFinalEntryPointCannotBeHandedASource() {
        assertThat(Arrays.stream(ReferenceScopedExtracts.class.getMethods())
                        .filter(method -> "loadNotaFinal".equals(method.getName()))
                        .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .containsExactly(NotaFinalContext.class, Function.class);
    }

    @Test
    void theNotaFinalReadsExactlyTheSevenPacksOneReferenceEach() {
        Map<String, String> references = new TreeMap<>(notaFinal(pec).referenceManifestSha256ByPack());
        references.remove(C7Pack.ID);
        SourceIdentity identity = pec.sourceIdentity();

        assertThatThrownBy(() -> new NotaFinalContext(IBGE, FIRST, identity, references))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly the packs");
    }

    // ---- the identity of a source

    @Test
    void theKeyOfASourceIsTheFirstSixteenHexDigitsOfTheSha256OfItsSortedCanonicalJson() throws Exception {
        SourceIdentity identity = pec.sourceIdentity();
        String json = identity.canonicalJson();

        String digest = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(JSON.readTree(json).propertyNames())
                .containsExactly("municipality_ibge", "pec_source_id", "pec_version", "postgres_version", "read_model");
        assertThat(json).doesNotContain("\n");
        assertThat(identity.key()).hasSize(16).isEqualTo(digest.substring(0, 16));
    }

    @Test
    void everyFieldOfTheIdentityIsPartOfItsKey() {
        SourceIdentity identity = pec.sourceIdentity();

        assertThat(Stream.of(
                                identity,
                                withPecVersion(identity),
                                withSourceId(identity),
                                withReadModel(identity),
                                pec.sourceIdentity(OTHER_POSTGRES),
                                new FixturePec(OTHER_IBGE).sourceIdentity())
                        .map(SourceIdentity::key))
                .doesNotHaveDuplicates();
    }

    // ---- helpers

    private ReferenceScopedExtracts cache() {
        return new ReferenceScopedExtracts(root, CLOCK);
    }

    /** A reference revision of each pack of its own, as the siblings of a Nota Final are. */
    private static String referenceOf(IndicatorRule rule) {
        List<String> packs = IndicatorRuleRegistry.all().stream()
                .map(each -> each.descriptor().id())
                .toList();
        return "%02x".formatted(packs.indexOf(rule.descriptor().id()) + 1).repeat(32);
    }

    private static ReferenceExecutionContext context(IndicatorRule rule, YearMonth month, FixturePec source) {
        return quadrimestre(rule, source).at(month);
    }

    private static QuadrimestreContext quadrimestre(IndicatorRule rule, FixturePec source) {
        return quadrimestre(rule, source, source.sourceIdentity());
    }

    private static QuadrimestreContext quadrimestre(IndicatorRule rule, FixturePec source, SourceIdentity identity) {
        return new QuadrimestreContext(
                source.sourceIdentity().municipalityIbge(),
                FIRST,
                referenceOf(rule),
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                identity);
    }

    private static NotaFinalContext notaFinal(FixturePec source) {
        Map<String, String> references = new TreeMap<>();
        IndicatorRuleRegistry.all()
                .forEach(rule -> references.put(rule.descriptor().id(), referenceOf(rule)));
        return new NotaFinalContext(IBGE, FIRST, source.sourceIdentity(), references);
    }

    /** Acquires C1 to C7 of the first quadrimestre; the fingerprint of each pack, by pack id. */
    private Map<String, String> acquireAll(FixturePec source) throws IOException {
        Map<String, String> fingerprints = new TreeMap<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            QuadrimestreInputs inputs = cache().loadOrAcquireQuadrimestre(quadrimestre(rule, source), source.inputs());
            fingerprints.put(rule.descriptor().id(), inputs.localSourceFingerprint());
        }
        return fingerprints;
    }

    /** The fingerprint of a quadrimestre acquired from {@code source} into a cache of its own. */
    private String fingerprintOf(String cacheName, QuadrimestreContext context, FixturePec source) throws IOException {
        return new ReferenceScopedExtracts(root.resolve(cacheName), CLOCK)
                .loadOrAcquireQuadrimestre(context, source.inputs())
                .localSourceFingerprint();
    }

    private Path sidecarFile(ReferenceExecutionContext context) {
        return cache().partitionOf(context).resolve(PartitionSidecar.FILE);
    }

    private String sidecarText(ReferenceExecutionContext context) throws IOException {
        return Files.readString(sidecarFile(context));
    }

    private PartitionSidecar sidecarOf(ReferenceExecutionContext context) throws IOException {
        return PartitionSidecar.fromJson(sidecarText(context));
    }

    private static Extract extractOf(ExtractionManifest manifest) {
        return new Extract(
                manifest.extractionId(), manifest.checksum(), manifest.queryChecksum(), manifest.adapterVersion());
    }

    /** Edits one manifest of the partition on disk, as another build or another hand would have left it. */
    private void rewrite(ReferenceExecutionContext context, String extractionId, Consumer<ObjectNode> edit)
            throws IOException {
        Path file = cache().partitionOf(context).resolve(extractionId + MANIFEST_SUFFIX);
        ObjectNode manifest = (ObjectNode) JSON.readTree(Files.readString(file));
        edit.accept(manifest);
        Files.writeString(file, JSON.writeValueAsString(manifest));
    }

    /**
     * The manifest as a build whose first part was read by an older adapter would have written it:
     * the version of the part and, as {@code ExtractValidation} demands, the composite query checksum
     * that follows from it.
     */
    private void readFirstPartWithAnOlderAdapter(ReferenceExecutionContext context, String extractionId)
            throws IOException {
        List<ManifestPart> parts = new ArrayList<>(new FileExtractStore(cache().partitionOf(context))
                .readManifest(extractionId)
                .parts());
        ManifestPart first = parts.getFirst();
        parts.set(
                0,
                new ManifestPart(
                        first.index(),
                        first.capability(),
                        OLDER_ADAPTER,
                        first.queryChecksum(),
                        first.recordKind(),
                        first.periodStart(),
                        first.periodEndExclusive(),
                        first.params(),
                        first.paramsChecksum(),
                        first.rowCount()));
        rewrite(context, extractionId, manifest -> {
            ((ObjectNode) manifest.path("parts").get(0)).put("adapterVersion", OLDER_ADAPTER);
            manifest.put("queryChecksum", ManifestChecksums.compositeQueryChecksum(parts));
        });
    }

    /** Makes the sidecar list what is on disk now: a partition whose own record agrees with its files. */
    private void resign(ReferenceExecutionContext context, String... extractionIds) throws IOException {
        FileExtractStore store = new FileExtractStore(cache().partitionOf(context));
        List<Extract> extracts = new ArrayList<>();
        for (String extractionId : extractionIds) {
            extracts.add(extractOf(store.readManifest(extractionId)));
        }
        Files.writeString(
                sidecarFile(context),
                PartitionSidecar.of(context, CLOCK.instant(), extracts).toJson());
    }

    private static void emptyGzip(Path file) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(new byte[0]);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** The partition exists and is not what {@code context} expects: refused, and the source is not read. */
    private void assertRefused(ReferenceExecutionContext context, String reason) {
        ReferenceScopedExtracts cache = cache();
        AcquisitionInputs live = pec.inputs();
        int requests = pec.requests().size();

        assertThatThrownBy(() -> cache.loadOrAcquire(context, live))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(reason);

        assertThat(pec.requests())
                .as("a partition that exists is refused, never replaced by a new read")
                .hasSize(requests);
    }

    private static ReferenceExecutionContext inMunicipality(ReferenceExecutionContext base, SourceIdentity identity) {
        return new ReferenceExecutionContext(
                identity.municipalityIbge(),
                base.quadrimestre(),
                base.referenceManifestSha256(),
                base.packId(),
                base.ruleVersion(),
                identity,
                base.month());
    }

    private static ReferenceExecutionContext ofReference(ReferenceExecutionContext base, String reference) {
        return new ReferenceExecutionContext(
                base.municipalityIbge(),
                base.quadrimestre(),
                reference,
                base.packId(),
                base.ruleVersion(),
                base.sourceIdentity(),
                base.month());
    }

    private static ReferenceExecutionContext ofRuleVersion(ReferenceExecutionContext base, String ruleVersion) {
        return new ReferenceExecutionContext(
                base.municipalityIbge(),
                base.quadrimestre(),
                base.referenceManifestSha256(),
                base.packId(),
                ruleVersion,
                base.sourceIdentity(),
                base.month());
    }

    private static ReferenceExecutionContext readFrom(ReferenceExecutionContext base, SourceIdentity identity) {
        return new ReferenceExecutionContext(
                base.municipalityIbge(),
                base.quadrimestre(),
                base.referenceManifestSha256(),
                base.packId(),
                base.ruleVersion(),
                identity,
                base.month());
    }

    private static ReferenceExecutionContext inMonth(ReferenceExecutionContext base, YearMonth month) {
        return new ReferenceExecutionContext(
                base.municipalityIbge(),
                base.quadrimestre(),
                base.referenceManifestSha256(),
                base.packId(),
                base.ruleVersion(),
                base.sourceIdentity(),
                month);
    }

    private static SourceIdentity withPecVersion(SourceIdentity base) {
        return new SourceIdentity(
                base.pecSourceId(),
                OTHER_PEC_VERSION,
                base.postgresVersion(),
                base.municipalityIbge(),
                base.readModel());
    }

    private static SourceIdentity withSourceId(SourceIdentity base) {
        return new SourceIdentity(
                OTHER_SOURCE, base.pecVersion(), base.postgresVersion(), base.municipalityIbge(), base.readModel());
    }

    private static SourceIdentity withReadModel(SourceIdentity base) {
        return new SourceIdentity(
                base.pecSourceId(), base.pecVersion(), base.postgresVersion(), base.municipalityIbge(), "PEC_OLTP");
    }
}
