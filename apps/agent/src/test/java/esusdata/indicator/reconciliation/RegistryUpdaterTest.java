package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.reconciliation.PackVerdict.Mode;
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

class RegistryUpdaterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GatePack PACK = GatePack.bySiapsCode(105).orElseThrow();
    private static final String VERSION = PACK.packId() + "@0.0.1";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final String SHA = "ab".repeat(32);

    private static final String REGISTRY = """
            [{"pack":"%1$s","rule_version":"%2$s",
              "gates":{"A":{"status":"PASSED","check":"fichas@1","note":"kept"},"D":{"status":"PENDING"}},
              "blocking_gaps_closed":["G1"],"extra":{"keep":true}},
             {"pack":"outro","rule_version":"outro@1","gates":{"D":{"status":"PENDING"}},"blocking_gaps_closed":[]}]
            """.formatted(PACK.packId(), VERSION);

    private static PackVerdict verdict(Mode mode, Status status) {
        return new PackVerdict(PACK, VERSION, mode, status, "", "2026Q2", List.of(), 0, List.of());
    }

    private static Path registry(Path directory) throws IOException {
        Path file = directory.resolve("release-gates.json");
        Files.writeString(file, REGISTRY);
        return file;
    }

    @Test
    void recordsOnlyTheDGateOfTheMatchingEntry(@TempDir Path directory) throws IOException {
        Path file = registry(directory);

        RegistryUpdater.record(
                file, verdict(Mode.GATE, Status.PASSED), DAY, "docs/indicadores/portoes/portao-d-x.md", SHA);

        JsonNode root = MAPPER.readTree(Files.readString(file));
        JsonNode d = root.get(0).path("gates").path("D");
        assertThat(d.path("status").asString()).isEqualTo("PASSED");
        assertThat(d.path("check").asString()).isEqualTo("siaps-distribuicao-por-classe@1");
        assertThat(d.path("checked_at").asString()).isEqualTo("2026-10-07");
        assertThat(d.path("evidence")).hasSize(1);
        assertThat(d.path("evidence").get(0).path("kind").asString()).isEqualTo("conciliacao-siaps");
        assertThat(d.path("evidence").get(0).path("ref").asString())
                .isEqualTo("docs/indicadores/portoes/portao-d-x.md");
        assertThat(d.path("evidence").get(0).path("sha256").asString()).isEqualTo(SHA);
        // everything else survives
        assertThat(root.get(0).path("gates").path("A").path("note").asString()).isEqualTo("kept");
        assertThat(root.get(0).path("blocking_gaps_closed").get(0).asString()).isEqualTo("G1");
        assertThat(root.get(0).path("extra").path("keep").asBoolean()).isTrue();
        assertThat(root.get(1).path("gates").path("D").path("status").asString())
                .isEqualTo("PENDING");
    }

    @Test
    void aFailedGateKeepsItsEvidenceAndAPendingOneHasNone(@TempDir Path directory) throws IOException {
        Path file = registry(directory);

        RegistryUpdater.record(file, verdict(Mode.GATE, Status.FAILED), DAY, "docs/x.md", SHA);
        assertThat(MAPPER.readTree(Files.readString(file))
                        .get(0)
                        .path("gates")
                        .path("D")
                        .path("status")
                        .asString())
                .isEqualTo("FAILED");

        RegistryUpdater.record(file, verdict(Mode.GATE, Status.PENDING), DAY, null, null);
        JsonNode d =
                MAPPER.readTree(Files.readString(file)).get(0).path("gates").path("D");
        assertThat(d.path("status").asString()).isEqualTo("PENDING");
        assertThat(d.path("evidence")).isEmpty();
    }

    @Test
    void neverRecordsAnInformativeResult(@TempDir Path directory) throws IOException {
        Path file = registry(directory);

        assertThatThrownBy(() ->
                        RegistryUpdater.record(file, verdict(Mode.INFORMATIVO, Status.PASSED), DAY, "docs/x.md", SHA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("informative");
        assertThat(Files.readString(file)).isEqualTo(REGISTRY);
    }

    @Test
    void refusesADecidedGateWithoutItsEvidence(@TempDir Path directory) throws IOException {
        Path file = registry(directory);

        assertThatThrownBy(() -> RegistryUpdater.record(file, verdict(Mode.GATE, Status.PASSED), DAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> RegistryUpdater.record(file, verdict(Mode.GATE, Status.PASSED), DAY, "docs/x.md", "nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverCreatesAnEntryAndMatchesTheRuleVersion(@TempDir Path directory) throws IOException {
        Path file = registry(directory);
        PackVerdict otherVersion = new PackVerdict(
                PACK, PACK.packId() + "@9", Mode.GATE, Status.PASSED, "", "2026Q2", List.of(), 0, List.of());

        assertThatThrownBy(() -> RegistryUpdater.record(file, otherVersion, DAY, "docs/x.md", SHA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no registry entry");
        assertThat(Files.readString(file)).isEqualTo(REGISTRY);
    }

    @Test
    void findsTheEntriesInsideAnObjectRoot(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("release-gates.json");
        Files.writeString(
                file,
                "{\"version\":1,\"entries\":[{\"pack\":\"%s\",\"rule_version\":\"%s\",\"gates\":{}}]}"
                        .formatted(PACK.packId(), VERSION));

        RegistryUpdater.record(file, verdict(Mode.GATE, Status.PASSED), DAY, "docs/x.md", SHA);

        JsonNode root = MAPPER.readTree(Files.readString(file));
        assertThat(root.path("entries")
                        .get(0)
                        .path("gates")
                        .path("D")
                        .path("status")
                        .asString())
                .isEqualTo("PASSED");
        assertThat(root.path("version").asInt()).isEqualTo(1);
    }
}
