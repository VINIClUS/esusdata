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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The updater against a temporary copy of the real {@code release-gates.json}, never the file itself. */
class RegistryUpdaterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REPO = Path.of("..", "..");
    private static final Path REAL = REPO.resolve("contracts/indicators/release-gates.json");
    private static final String EVIDENCE = "docs/indicadores/portoes/portao-d-conciliacao-siaps.md";
    private static final PackDescriptor DESCRIPTOR = new C4Pack().descriptor();
    private static final GatePack PACK = GatePack.byPackId(DESCRIPTOR.id()).orElseThrow();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    private static PackVerdict verdict(ReferencePurpose purpose, Status status) {
        return new PackVerdict(
                PACK,
                DESCRIPTOR.ruleVersion(),
                purpose,
                status,
                "",
                "2026Q2",
                List.of(),
                0,
                List.of(),
                PackVerdict.NO_LOCAL_SOURCE);
    }

    private static Path copyOfTheRealRegistry(Path directory) throws IOException {
        Path file = directory.resolve("release-gates.json");
        Files.copy(REAL, file);
        return file;
    }

    private static String sha() throws IOException {
        return SummaryWriter.sha256(REPO.resolve(EVIDENCE));
    }

    private static JsonNode gateD(Path file, String pack) throws IOException {
        for (JsonNode entry : MAPPER.readTree(Files.readString(file)).path("packs")) {
            if (entry.path("pack").asString().equals(pack)) {
                return entry.path("gates").path("D");
            }
        }
        throw new AssertionError(pack);
    }

    @Test
    void aPassedGateIsWrittenInTheShapeTheLoaderTheSchemaAndTheConsistencyCheckAccept(@TempDir Path directory)
            throws Exception {
        Path file = copyOfTheRealRegistry(directory);
        String before = Files.readString(file);

        RegistryUpdater.record(file, REPO, verdict(ReferencePurpose.GATE, Status.PASSED), DAY, EVIDENCE, sha());

        JsonNode d = gateD(file, DESCRIPTOR.id());
        assertThat(d.path("status").asString()).isEqualTo("PASSED");
        assertThat(d.path("check").asString()).isEqualTo("siaps-distribuicao-por-classe@1");
        assertThat(d.path("checked_at").asString()).isEqualTo("2026-10-07");
        assertThat(d.path("evidence")).hasSize(1);
        assertThat(d.path("evidence").get(0).path("kind").asString()).isEqualTo("conciliacao-siaps");

        // the loader accepts it
        String written = Files.readString(file);
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
        assertThat(SummaryWriter.sha256(REPO.resolve(evidence.ref()))).isEqualTo(evidence.sha256());

        // only D of that pack changed: the same document, with that one gate put back, is the original
        Path restored = directory.resolve("restored.json");
        Files.writeString(restored, before);
        assertThat(MAPPER.readTree(written).path("packs").size())
                .isEqualTo(MAPPER.readTree(before).path("packs").size());
        assertThat(gateD(restored, "c1-mais-acesso")).isEqualTo(gateD(file, "c1-mais-acesso"));
        assertThat(MAPPER.readTree(written).path("packs").get(0))
                .isEqualTo(MAPPER.readTree(before).path("packs").get(0));
    }

    @Test
    void aFailedGateIsValidAndAPendingOneCarriesNoCheckNorDate(@TempDir Path directory) throws Exception {
        Path file = copyOfTheRealRegistry(directory);

        RegistryUpdater.record(file, REPO, verdict(ReferencePurpose.GATE, Status.FAILED), DAY, EVIDENCE, sha());
        assertThat(gateD(file, DESCRIPTOR.id()).path("status").asString()).isEqualTo("FAILED");
        assertThat(ReleaseGateRegistry.fromJson(Files.readString(file))
                        .statusOf(DESCRIPTOR)
                        .check(GateId.D)
                        .check())
                .isEqualTo("siaps-distribuicao-por-classe@1");

        RegistryUpdater.record(file, REPO, verdict(ReferencePurpose.GATE, Status.PENDING), DAY, null, null);
        JsonNode d = gateD(file, DESCRIPTOR.id());
        assertThat(d.path("status").asString()).isEqualTo("PENDING");
        assertThat(d.has("check")).isFalse();
        assertThat(d.has("checked_at")).isFalse();
        assertThat(d.path("evidence")).isEmpty();
        assertThat(ReleaseGateRegistry.fromJson(Files.readString(file))
                        .statusOf(DESCRIPTOR)
                        .check(GateId.D)
                        .isPassed())
                .isFalse();
    }

    @Test
    void neverRecordsADiagnosticResult(@TempDir Path directory) throws Exception {
        Path file = copyOfTheRealRegistry(directory);
        String before = Files.readString(file);

        assertThatThrownBy(() -> RegistryUpdater.record(
                        file, REPO, verdict(ReferencePurpose.DIAGNOSTIC, Status.PASSED), DAY, EVIDENCE, sha()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diagnostic");
        assertThatThrownBy(() -> RegistryUpdater.record(
                        file, REPO, verdict(ReferencePurpose.DIAGNOSTIC, Status.PENDING), DAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void refusesADecidedGateWhoseEvidenceIsMissingOrDoesNotMatch(@TempDir Path directory) throws Exception {
        Path file = copyOfTheRealRegistry(directory);
        String before = Files.readString(file);
        PackVerdict passed = verdict(ReferencePurpose.GATE, Status.PASSED);

        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, passed, DAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, passed, DAY, EVIDENCE, "nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, passed, DAY, "docs/does-not-exist.md", sha()))
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, passed, DAY, EVIDENCE, "ab".repeat(32)))
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, passed, DAY, "../../etc/hostname", sha()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void neverCreatesAnEntryAndMatchesTheRuleVersion(@TempDir Path directory) throws Exception {
        Path file = copyOfTheRealRegistry(directory);
        String before = Files.readString(file);
        PackVerdict otherVersion = new PackVerdict(
                PACK,
                PACK.packId() + "@9.9.9",
                ReferencePurpose.GATE,
                Status.PASSED,
                "",
                "2026Q2",
                List.of(),
                0,
                List.of(),
                PackVerdict.NO_LOCAL_SOURCE);

        assertThatThrownBy(() -> RegistryUpdater.record(file, REPO, otherVersion, DAY, EVIDENCE, sha()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no registry entry");
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void theRealRegistryIsNeverTheOneWritten() throws Exception {
        assertThat(Files.readString(REAL)).contains("\"schema_version\": \"1\"");
    }
}
