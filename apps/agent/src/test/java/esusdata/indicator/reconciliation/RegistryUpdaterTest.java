package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.PortaoDEvidenceFixtures.Tree;
import esusdata.indicator.reconciliation.RegistryUpdater.CitedFile;
import esusdata.indicator.reconciliation.RegistryUpdater.EvidenceBundle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The updater against a temporary copy of the real {@code release-gates.json}, never the file
 * itself, with the evidence of a set built by the real writers ({@link PortaoDEvidenceFixtures}).
 */
class RegistryUpdaterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REPO = Path.of("..", "..");
    private static final Path REAL = REPO.resolve("contracts/indicators/release-gates.json");
    private static final PackDescriptor DESCRIPTOR = new C4Pack().descriptor();
    private static final LocalDate DAY = PortaoDEvidenceFixtures.DAY;

    private static JsonNode gateD(Path file, String pack) throws IOException {
        for (JsonNode entry : MAPPER.readTree(Files.readString(file)).path("packs")) {
            if (entry.path("pack").asString().equals(pack)) {
                return entry.path("gates").path("D");
            }
        }
        throw new AssertionError(pack);
    }

    private static ReferenceSetVerdict with(ReferenceSetVerdict verdict, Status status, String check) {
        return new ReferenceSetVerdict(
                verdict.pack(),
                verdict.ruleVersion(),
                check,
                verdict.gateSetSha256(),
                status,
                verdict.reason(),
                verdict.references());
    }

    private static ReferenceSetVerdict pending(Tree tree) throws IOException {
        ReferenceSet set =
                tree.loadedPolicy().referenceSet(tree.packId(), tree.verdict().ruleVersion());
        return ReferenceSetVerdict.aggregate(set, Map.of(), Map.of());
    }

    @Test
    void aPassedGateIsWrittenInTheShapeTheLoaderTheSchemaAndTheConsistencyCheckAccept(@TempDir Path directory)
            throws Exception {
        String before = Files.readString(REAL);

        Tree tree = PortaoDEvidenceFixtures.decided(directory, DESCRIPTOR, Status.PASSED);

        JsonNode d = gateD(tree.registry(), DESCRIPTOR.id());
        assertThat(d.path("status").asString()).isEqualTo("PASSED");
        assertThat(d.path("check").asString()).isEqualTo("siaps-distribuicao-por-classe@2");
        assertThat(d.path("checked_at").asString()).isEqualTo("2026-10-09");
        assertThat(d.path("evidence")).hasSize(1);
        assertThat(d.path("evidence").get(0).path("kind").asString()).isEqualTo("conciliacao-siaps");

        // the loader accepts it
        String written = Files.readString(tree.registry());
        GateCheck loaded =
                ReleaseGateRegistry.fromJson(written).statusOf(DESCRIPTOR).check(GateId.D);
        assertThat(loaded.isPassed()).isTrue();
        // the schema accepts it
        try (var stream = getClass().getResourceAsStream("/indicators/release-gates.schema.json")) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);
            assertThat(schema.validate(MAPPER.readTree(written))).isEmpty();
        }
        // the consistency check (existing file, same sha256) holds
        GateCheck.Evidence evidence = loaded.evidence().getFirst();
        assertThat(SummaryWriter.sha256(tree.root().resolve(evidence.ref()))).isEqualTo(evidence.sha256());
        assertThat(PortaoDEvidenceChecks.registryProblems(MAPPER.readTree(written), tree.loadedPolicy(), tree.root()))
                .isEmpty();

        // only D of that pack changed: every other entry is the original one
        JsonNode original = MAPPER.readTree(before).path("packs");
        JsonNode now = MAPPER.readTree(written).path("packs");
        assertThat(now.size()).isEqualTo(original.size());
        for (int i = 0; i < original.size(); i++) {
            if (!DESCRIPTOR.id().equals(original.get(i).path("pack").asString())) {
                assertThat(now.get(i)).isEqualTo(original.get(i));
            }
        }
    }

    @Test
    void aFailedGateIsValidAndAPendingOneCarriesNoCheckNorDate(@TempDir Path directory) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(directory, DESCRIPTOR, Status.FAILED);

        assertThat(gateD(tree.registry(), DESCRIPTOR.id()).path("status").asString())
                .isEqualTo("FAILED");
        assertThat(ReleaseGateRegistry.fromJson(Files.readString(tree.registry()))
                        .statusOf(DESCRIPTOR)
                        .check(GateId.D)
                        .check())
                .isEqualTo("siaps-distribuicao-por-classe@2");

        RegistryUpdater.record(tree.registry(), tree.root(), pending(tree), DAY, EvidenceBundle.none());

        JsonNode d = gateD(tree.registry(), DESCRIPTOR.id());
        assertThat(d.path("status").asString()).isEqualTo("PENDING");
        assertThat(d.has("check")).isFalse();
        assertThat(d.has("checked_at")).isFalse();
        assertThat(d.path("evidence")).isEmpty();
        assertThat(ReleaseGateRegistry.fromJson(Files.readString(tree.registry()))
                        .statusOf(DESCRIPTOR)
                        .check(GateId.D)
                        .isPassed())
                .isFalse();
    }

    @Test
    void refusesABundleOfAnotherSetOrWithAFileThatIsMissingOrChanged(@TempDir Path directory) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(directory, DESCRIPTOR, Status.PASSED);
        String before = Files.readString(tree.registry());
        EvidenceBundle bundle = tree.bundle();
        List<CitedFile> cited = bundle.cited();

        assertThatThrownBy(() -> record(tree, tree.verdict(), withSet(bundle, "ab".repeat(32))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gate_set_sha256");
        assertThatThrownBy(() -> record(tree, tree.verdict(), EvidenceBundle.none()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        record(tree, tree.verdict(), withSummary(bundle, "docs/nope.json", bundle.summarySha256())))
                .hasMessageContaining("does not exist");
        assertThatThrownBy(
                        () -> record(tree, tree.verdict(), withSummary(bundle, bundle.summaryRef(), "ab".repeat(32))))
                .hasMessageContaining("does not match");
        assertThatThrownBy(
                        () -> record(tree, tree.verdict(), withSummary(bundle, "../../etc/hostname", "ab".repeat(32))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record(
                        tree,
                        tree.verdict(),
                        new EvidenceBundle(
                                bundle.summaryRef(),
                                bundle.summarySha256(),
                                bundle.gateSetSha256(),
                                List.of(new CitedFile(cited.getFirst().ref(), "ab".repeat(32))))))
                .hasMessageContaining("does not match");
        // a file that exists and has its hash, but is not the one the reference pins
        assertThatThrownBy(() -> record(
                        tree,
                        tree.verdict(),
                        new EvidenceBundle(
                                bundle.summaryRef(),
                                bundle.summarySha256(),
                                bundle.gateSetSha256(),
                                List.of(cited.getFirst()))))
                .hasMessageContaining("does not cite");
        assertThat(Files.readString(tree.registry())).isEqualTo(before);
    }

    @Test
    void refusesADecidedStatusThatDoesNotFollowFromTheReferencesAndACheckThatIsNotTheSets(@TempDir Path directory)
            throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(directory, DESCRIPTOR, Status.PASSED);
        String before = Files.readString(tree.registry());

        assertThatThrownBy(() -> record(
                        tree, with(tree.verdict(), Status.FAILED, tree.verdict().check()), tree.bundle()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not follow");
        assertThatThrownBy(() -> record(
                        tree, with(tree.verdict(), Status.PASSED, "siaps-distribuicao-por-classe@1"), tree.bundle()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("siaps-distribuicao-por-classe@2");
        assertThatThrownBy(() -> record(
                        tree, with(tree.verdict(), Status.PASSED, "siaps-nota-final-por-classe@2"), tree.bundle()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(tree.registry())).isEqualTo(before);
    }

    @Test
    void neverCreatesAnEntryAndMatchesTheRuleVersion(@TempDir Path directory) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(directory, DESCRIPTOR, Status.PASSED);
        String before = Files.readString(tree.registry());
        ReferenceSetVerdict otherVersion = new ReferenceSetVerdict(
                tree.verdict().pack(),
                tree.verdict().pack() + "@9.9.9",
                tree.verdict().check(),
                tree.verdict().gateSetSha256(),
                Status.PASSED,
                "",
                tree.verdict().references());

        assertThatThrownBy(() -> record(tree, otherVersion, tree.bundle()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no registry entry");
        assertThat(Files.readString(tree.registry())).isEqualTo(before);
    }

    @Test
    void theRealRegistryIsNeverTheOneWritten() throws Exception {
        assertThat(Files.readString(REAL)).contains("\"schema_version\": \"1\"");
    }

    private static void record(Tree tree, ReferenceSetVerdict verdict, EvidenceBundle bundle) throws IOException {
        RegistryUpdater.record(tree.registry(), tree.root(), verdict, DAY, bundle);
    }

    private static EvidenceBundle withSet(EvidenceBundle bundle, String gateSetSha256) {
        return new EvidenceBundle(bundle.summaryRef(), bundle.summarySha256(), gateSetSha256, bundle.cited());
    }

    private static EvidenceBundle withSummary(EvidenceBundle bundle, String ref, String sha256) {
        return new EvidenceBundle(ref, sha256, bundle.gateSetSha256(), bundle.cited());
    }
}
