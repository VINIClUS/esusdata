package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.PackDescriptor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The build-time half of the registry's validation (ADR 0032). The jar carries the registry but
 * not the documents its evidence points at, so the loader checks their shape only; here, against
 * the repository, every registered pack has an entry at the rule version compiled into this
 * release, and every evidence reference of a passed gate exists and still has the recorded SHA-256.
 * A bumped {@code rule_version} without a new entry fails this test — the approval is voided, not
 * silently carried over.
 */
class ReleaseGatesConsistencyTest {

    private static final Path REPO = Path.of("..", "..");
    private static final Path SOURCE = REPO.resolve("contracts/indicators/release-gates.json");

    static Stream<PackDescriptor> registered() {
        return ReleaseGateRegistry.registeredPacks().stream();
    }

    @Test
    void thePackagedRegistryIsTheContractFileOfTheRepository() throws Exception {
        try (InputStream packaged = getClass().getResourceAsStream(ReleaseGateRegistry.RESOURCE)) {
            assertThat(packaged).isNotNull();
            assertThat(packaged.readAllBytes()).isEqualTo(Files.readAllBytes(SOURCE));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void everyRegisteredPackHasAnEntryForItsCurrentRuleVersion(PackDescriptor descriptor) throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(SOURCE, StandardCharsets.UTF_8));
        assertThat(root.get("packs")).as("entries of %s", descriptor.id()).anySatisfy(entry -> {
            assertThat(entry.get("pack").asString()).isEqualTo(descriptor.id());
            assertThat(entry.get("rule_version").asString()).isEqualTo(descriptor.ruleVersion());
        });
        GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
        assertThat(status.stale())
                .as("%s: the registry only knows an older rule_version", descriptor.id())
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void everyEvidenceReferenceOfAPassedGateExistsAndMatchesItsHash(PackDescriptor descriptor) throws Exception {
        GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
        for (GateId id : GateId.values()) {
            for (GateCheck.Evidence evidence : status.check(id).evidence()) {
                Path document = REPO.resolve(evidence.ref());
                assertThat(document)
                        .as("%s gate %s evidence %s", descriptor.id(), id, evidence.ref())
                        .exists();
                assertThat(sha256(document))
                        .as("%s gate %s: %s changed since its check passed", descriptor.id(), id, evidence.ref())
                        .isEqualTo(evidence.sha256());
            }
        }
    }

    @Test
    void noPackShipsWithAGatePassedByAnAgentWithoutItsEvidence() {
        for (PackDescriptor descriptor : ReleaseGateRegistry.registeredPacks()) {
            GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
            for (GateId id : GateId.values()) {
                if (status.check(id).isPassed()) {
                    assertThat(status.check(id).evidence()).isNotEmpty();
                    assertThat(status.check(id).check()).isNotBlank();
                }
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
