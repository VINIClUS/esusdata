package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** ADR 0032: the registry is validated at load and an approval never survives a version bump. */
class ReleaseGateRegistryTest {

    private static final PackDescriptor C1 = new C1Pack().descriptor();
    private static final PackDescriptor C2 = new C2Pack().descriptor();

    private static final String PENDING = "{\"status\":\"PENDING\",\"evidence\":[]}";
    private static final String PASSED = "{\"status\":\"PASSED\",\"check\":\"conferencia-fichas@1\","
            + "\"checked_at\":\"2026-10-06\",\"evidence\":[{\"kind\":\"doc\",\"ref\":\"docs/x.md\",\"sha256\":\""
            + GateFixtures.SHA + "\"}]}";

    private static String entry(PackDescriptor d, String gateA, String gateD) {
        return entry(d.id(), d.ruleVersion(), gateA, gateD);
    }

    private static String entry(String pack, String version, String gateA, String gateD) {
        return "{\"pack\":\"" + pack + "\",\"rule_version\":\"" + version + "\",\"blocking_gaps_closed\":[],"
                + "\"gates\":{\"A\":" + gateA + ",\"D\":" + gateD + "}}";
    }

    private static String file(String... entries) {
        return "{\"schema_version\":\"1\",\"packs\":[" + String.join(",", entries) + "]}";
    }

    private static ReleaseGateRegistry load(String json, PackDescriptor... registered) {
        return ReleaseGateRegistry.fromJson(json, List.of(registered));
    }

    @Test
    void thePackagedRegistryCoversEveryRegisteredPackWithGateDPendingAndAPassedForC1ToC7() {
        ReleaseGateRegistry bundled = ReleaseGateRegistry.bundled();

        for (PackDescriptor d : ReleaseGateRegistry.registeredPacks()) {
            GateStatus status = bundled.statusOf(d);
            assertThat(status.stale()).as(d.id()).isFalse();
            GateCheck.State expectedA = d.id().matches("c[1-7]-.*") ? GateCheck.State.PASSED : GateCheck.State.PENDING;
            assertThat(status.check(GateId.A).state()).as(d.id()).isEqualTo(expectedA);
            assertThat(status.check(GateId.D).state()).as(d.id()).isEqualTo(GateCheck.State.PENDING);
            assertThat(status.isComplete()).isFalse();
        }
    }

    @Test
    void aPassedEntryForTheCompiledVersionCountsForGatesAAndDOnly() {
        GateStatus status = load(file(entry(C1, PASSED, PENDING)), C1).statusOf(C1);

        assertThat(status.check(GateId.A).isPassed()).isTrue();
        assertThat(status.check(GateId.A).evidence()).hasSize(1);
        assertThat(status.check(GateId.D).isPassed()).isFalse();
        assertThat(status.check(GateId.B).isPassed()).isFalse();
        assertThat(status.check(GateId.C).isPassed()).isFalse();
        assertThat(status.incompleteReasons()).hasSize(3).doesNotContain(GateId.A.incompleteReason());
    }

    @Test
    void anEntryForAnOlderRuleVersionNeverCountsAndIsFlaggedStale() {
        String older = entry(C1.id(), C1.id() + "@0.1.0", PASSED, PASSED);

        GateStatus status = load(file(older), C1).statusOf(C1);

        assertThat(status.stale()).isTrue();
        assertThat(status.check(GateId.A).isPassed()).isFalse();
        assertThat(status.check(GateId.D).isPassed()).isFalse();
        assertThat(status.incompleteReasons()).hasSize(4);
    }

    @Test
    void aPassedGateWithoutEvidenceIsRejected() {
        String noEvidence = "{\"status\":\"PASSED\",\"check\":\"conferencia-fichas@1\",\"checked_at\":\"2026-10-06\","
                + "\"evidence\":[]}";
        assertThatThrownBy(() -> load(file(entry(C1, noEvidence, PENDING)), C1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSED needs evidence");
    }

    @Test
    void aPassedGateWithoutItsCheckOrDateIsRejected() {
        String noCheck = "{\"status\":\"PASSED\",\"checked_at\":\"2026-10-06\",\"evidence\":[{\"kind\":\"doc\","
                + "\"ref\":\"docs/x.md\",\"sha256\":\"" + GateFixtures.SHA + "\"}]}";
        String noDate = "{\"status\":\"PASSED\",\"check\":\"conferencia-fichas@1\",\"evidence\":[{\"kind\":\"doc\","
                + "\"ref\":\"docs/x.md\",\"sha256\":\"" + GateFixtures.SHA + "\"}]}";
        assertThatThrownBy(() -> load(file(entry(C1, noCheck, PENDING)), C1)).hasMessageContaining("needs a check");
        assertThatThrownBy(() -> load(file(entry(C1, noDate, PENDING)), C1)).hasMessageContaining("checked_at");
    }

    @Test
    void aPendingGateThatNamesACheckIsRejectedSoNobodyHalfApproves() {
        String half = "{\"status\":\"PENDING\",\"check\":\"conferencia-fichas@1\",\"evidence\":[]}";
        assertThatThrownBy(() -> load(file(entry(C1, half, PENDING)), C1)).hasMessageContaining("PENDING");
    }

    @Test
    void evidenceMustCarryAWellFormedSha256() {
        String badSha = PASSED.replace(GateFixtures.SHA, "abc");
        assertThatThrownBy(() -> load(file(entry(C1, badSha, PENDING)), C1)).hasMessageContaining("sha256");
    }

    @Test
    void aRegisteredPackWithoutAnEntryStopsTheLoad() {
        assertThatThrownBy(() -> load(file(entry(C1, PENDING, PENDING)), C1, C2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no entry for registered pack " + C2.id());
    }

    @Test
    void aDuplicatedEntryStopsTheLoad() {
        String one = entry(C1, PENDING, PENDING);
        assertThatThrownBy(() -> load(file(one, one), C1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate entry");
    }

    @Test
    void anEntryForAnUnregisteredPackStopsTheLoad() {
        assertThatThrownBy(() -> load(file(entry(C1, PENDING, PENDING), entry(C2, PENDING, PENDING)), C1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unregistered pack");
    }

    @Test
    void theFileCannotCarryTheGatesTheExecutorEvaluates() {
        String withB = "{\"pack\":\"" + C1.id() + "\",\"rule_version\":\"" + C1.ruleVersion()
                + "\",\"blocking_gaps_closed\":[],\"gates\":{\"A\":" + PENDING + ",\"B\":" + PENDING + ",\"D\":"
                + PENDING + "}}";
        assertThatThrownBy(() -> load(file(withB), C1)).hasMessageContaining("only A and D");
    }

    @Test
    void brokenJsonOrAWrongSchemaVersionNeverFallsBackToNoGates() {
        assertThatThrownBy(() -> load("{not json", C1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> load("{\"schema_version\":\"2\",\"packs\":[]}", C1))
                .hasMessageContaining("schema_version");
        assertThatThrownBy(() -> load("{\"schema_version\":\"1\"}", C1)).hasMessageContaining("packs");
    }

    @Test
    void aPackTheRegistryHasNeverHeardOfIsAllPendingNotStale() {
        GateStatus status = load(file(entry(C1, PASSED, PASSED)), C1).statusOf(C2);

        assertThat(status.stale()).isFalse();
        assertThat(status.isComplete()).isFalse();
    }

    @Test
    void theSnapshotRecordsPackVersionStalenessAndEveryGate() throws Exception {
        GateStatus status = load(file(entry(C1, PASSED, PENDING)), C1).statusOf(C1);

        var json = new ObjectMapper().readTree(ReleaseGateRegistry.snapshotJson(status));

        assertThat(json.get("pack").asString()).isEqualTo(C1.id());
        assertThat(json.get("rule_version").asString()).isEqualTo(C1.ruleVersion());
        assertThat(json.get("stale").asBoolean()).isFalse();
        assertThat(json.get("gates").propertyNames()).containsExactly("A", "B", "C", "D");
        assertThat(json.get("gates").get("A").get("status").asString()).isEqualTo("PASSED");
        assertThat(json.get("gates").get("A").get("evidence").get(0).asString()).isEqualTo("docs/x.md");
    }
}
