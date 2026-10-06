package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c1.C1Pack;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The packaged registry satisfies its JSON Schema, and the schema and the loader — which does not
 * run the schema at runtime — refuse the same bad documents, so they cannot drift apart.
 */
class ReleaseGatesSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PackDescriptor C1 = new C1Pack().descriptor();

    private static Schema schema() throws Exception {
        try (InputStream stream = resource("/indicators/release-gates.schema.json")) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = ReleaseGatesSchemaTest.class.getResourceAsStream(name);
        assertThat(stream).as("missing classpath resource %s", name).isNotNull();
        return stream;
    }

    private static ObjectNode packaged() throws Exception {
        try (InputStream stream = resource(ReleaseGateRegistry.RESOURCE)) {
            return (ObjectNode) MAPPER.readTree(stream);
        }
    }

    private static ObjectNode gate(ObjectNode root, String pack, String gate) {
        for (JsonNode entry : root.get("packs")) {
            if (entry.get("pack").asString().equals(pack)) {
                return (ObjectNode) entry.get("gates").get(gate);
            }
        }
        throw new AssertionError(pack);
    }

    @Test
    void thePackagedRegistryValidatesAgainstItsSchema() throws Exception {
        assertThat(schema().validate(packaged())).isEmpty();
    }

    @Test
    void aPassedGateWithoutEvidenceIsRefusedByTheSchemaAndByTheLoader() throws Exception {
        ObjectNode root = packaged();
        ObjectNode a = gate(root, C1.id(), "A");
        a.put("status", "PASSED").put("check", "conferencia-fichas@1").put("checked_at", "2026-10-06");

        assertThat(schema().validate(root)).isNotEmpty();
        assertThatThrownBy(() -> ReleaseGateRegistry.fromJson(root.toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDuplicatedEntryIsRefusedByTheLoaderAndAMissingPackTooWhileTheSchemaKeepsItsShape() throws Exception {
        ObjectNode duplicated = packaged();
        ((ArrayNode) duplicated.get("packs")).add(duplicated.get("packs").get(0));
        assertThatThrownBy(() -> ReleaseGateRegistry.fromJson(duplicated.toString()))
                .hasMessageContaining("duplicate");

        ObjectNode missing = packaged();
        ((ArrayNode) missing.get("packs")).remove(0);
        assertThatThrownBy(() -> ReleaseGateRegistry.fromJson(missing.toString()))
                .hasMessageContaining("no entry for registered pack");
        assertThat(schema().validate(missing)).isEmpty();
    }

    @Test
    void aPendingGateThatNamesACheckAndAGateOutsideAAndDAreRefusedByBoth() throws Exception {
        ObjectNode half = packaged();
        gate(half, C1.id(), "A").put("check", "conferencia-fichas@1");
        assertThat(schema().validate(half)).isNotEmpty();
        assertThatThrownBy(() -> ReleaseGateRegistry.fromJson(half.toString()))
                .isInstanceOf(IllegalStateException.class);

        ObjectNode withB = packaged();
        ((ObjectNode) withB.get("packs").get(0).get("gates"))
                .set("B", MAPPER.readTree("{\"status\":\"PENDING\",\"evidence\":[]}"));
        assertThat(schema().validate(withB)).isNotEmpty();
        assertThatThrownBy(() -> ReleaseGateRegistry.fromJson(withB.toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPassedGateWithEveryFieldIsAcceptedByBoth() throws Exception {
        ObjectNode root = packaged();
        ObjectNode a = gate(root, C1.id(), "A");
        a.put("status", "PASSED").put("check", "conferencia-fichas@1").put("checked_at", "2026-10-06");
        a.set(
                "evidence",
                MAPPER.readTree("[{\"kind\":\"doc\",\"ref\":\"docs/x.md\",\"sha256\":\"" + TestGates.SHA + "\"}]"));

        assertThat(schema().validate(root)).isEmpty();
        assertThat(ReleaseGateRegistry.fromJson(root.toString())
                        .statusOf(C1)
                        .check(GateId.A)
                        .isPassed())
                .isTrue();
    }
}
