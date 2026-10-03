package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ENG-43 guard of the foundation capabilities (ADR 0030): each one has exactly one {@code NOT_TESTED}
 * entry in {@code contracts/compatibility/pec-adapters.json} pinned to the query that runs and to
 * the synthetic fixture it was tested on, listing exactly the tables and columns the query reads —
 * and the three {@code VALIDATED} entries of C1, isolation and coverage stay byte-for-byte what was
 * approved. Editing a query or the fixture without updating the matrix fails here, not in review.
 */
class CapabilityMatrixConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UNIQUE_KEY = "UNIQUE_KEY=";
    private static final String PERSON_GROUP = "tb_dim_cidadao_pec_grupo";

    /**
     * SHA-256 of each approved entry as Jackson writes it back: a change to any of their fields,
     * fingerprints included, is a new validation (ADR 0023), never an edit.
     */
    private static final Map<String, String> VALIDATED_ENTRIES = Map.of(
            "individual_encounter_modality",
            "sha256:8810a6b8881f6374576550f18accfc559ef92ed574ab6ba9dea7e826b21add23",
            "municipal_isolation",
            "sha256:d89c9f93a700dc90bc3d4c8e469d00f7c9959a1d7ebc24e6a0f5fd2f3ffe2970",
            "period_coverage",
            "sha256:ae53dad55d31f774aa14b5d8f34def1846f5333eaeafdb5083e0e24ad659e2f9");

    static Stream<String> foundation() {
        return Capabilities.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyFoundationCapabilityHasOneNotTestedEntryPinnedToItsQueryAndFixture(String capability) throws IOException {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        List<JsonNode> entries = entries(capability);

        assertThat(entries).as(capability).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(text(entry, "status")).isEqualTo("NOT_TESTED");
        assertThat(text(entry, "test_result")).isEqualTo("NOT_RUN");
        assertThat(entry.has("approved_at") || entry.has("approved_by")).isFalse();
        assertThat(text(entry, "adapter_version")).isEqualTo(contract.adapterVersion());
        assertThat(text(entry, "query_checksum"))
                .as(capability + ": update the matrix entry when the query changes")
                .isEqualTo(FrozenQuery.checksum(contract.queryText()))
                .isEqualTo(contract.queryChecksum());
        assertThat(text(entry, "fixture_checksum"))
                .as(capability + ": update the matrix entry when the fixture changes")
                .isEqualTo(CapabilityFixture.checksum());
        assertThat(strings(entry.get("pec_versions"))).containsExactly("5.5.28");
        assertThat(text(entry, "postgresql_version")).isEqualTo("9.6.13");
        assertThat(text(entry, "read_model")).isEqualTo("PEC_DW");
        assertThat(text(entry, "installation_role")).isEqualTo("PRONTUARIO");
        assertThat(text(entry, "status_reason")).contains("ADR 0023").contains("fixture");
        assertThat(text(entry.get("municipal_isolation_evidence"), "binding_column"))
                .isEqualTo("tb_dim_municipio.co_ibge");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void objectsUsedAreExactlyTheTablesAndColumnsTheQueryReads(String capability) {
        String query = CapabilityCatalog.packaged().require(capability).queryText();
        SortedMap<String, SortedSet<String>> listed = new TreeMap<>();
        for (JsonNode object : entries(capability).get(0).get("objects_used")) {
            SortedSet<String> columns = new TreeSet<>();
            for (String column : strings(object.get("columns_used"))) {
                if (!column.contains("=")) {
                    columns.add(column);
                }
            }
            listed.put(text(object, "object"), columns);
        }

        assertThat(listed.keySet()).as(capability).containsExactlyElementsOf(CapabilitySql.tablesRead(query));
        assertThat(listed).as(capability).isEqualTo(CapabilitySql.columnsRead(query));
    }

    /**
     * The probe understands one {@code UNIQUE_KEY=} per object (the Rust plane keeps one). It is on
     * every dimension joined by its surrogate key — a duplicated key would multiply the fact rows —
     * and never on the person group, which the query aggregates. {@code REQUIRED_DIMENSIONS=} and
     * {@code LEAF_SEMANTICS=} are C1's and are fixed to its objects.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void markersAreOneUniqueKeyOnListedColumnsWhereTheKeySustainsTheRowIdentity(String capability) {
        for (JsonNode object : entries(capability).get(0).get("objects_used")) {
            String table = text(object, "object");
            List<String> columns = strings(object.get("columns_used"));
            List<String> markers =
                    columns.stream().filter(column -> column.contains("=")).toList();

            assertThat(markers).as(capability + " " + table).allMatch(marker -> marker.startsWith(UNIQUE_KEY));
            assertThat(markers).as(capability + " " + table).hasSizeLessThanOrEqualTo(1);
            for (String marker : markers) {
                assertThat(columns)
                        .as(capability + " " + table)
                        .containsAll(CompatibilityFingerprint.parseUniqueKeyColumns(marker));
            }
            if (table.startsWith("tb_dim_") && !PERSON_GROUP.equals(table)) {
                assertThat(markers).as(capability + " " + table).hasSize(1);
            }
            if (PERSON_GROUP.equals(table)) {
                assertThat(markers).as(capability + " " + table).isEmpty();
            }
        }
    }

    @Test
    void theValidatedEntriesStayExactlyAsApproved() throws Exception {
        for (Map.Entry<String, String> approved : VALIDATED_ENTRIES.entrySet()) {
            List<JsonNode> entries = entries(approved.getKey());

            assertThat(entries).as(approved.getKey()).hasSize(1);
            assertThat(text(entries.get(0), "status")).isEqualTo("VALIDATED");
            assertThat(sha256(MAPPER.writeValueAsString(entries.get(0))))
                    .as(approved.getKey() + " was approved with ADR 0023 evidence; a change is a new validation")
                    .isEqualTo(approved.getValue());
        }
        assertThat(text(entries("individual_encounter_modality").get(0), "query_checksum"))
                .isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
        assertThat(text(entries("municipal_isolation").get(0), "fixture_checksum"))
                .isEqualTo(
                        sha256(Files.readAllBytes(Path.of("src/test/resources/fixtures/pec_synthetic_fixture.sql"))));
    }

    @Test
    void everyEntryIsApprovedOrAFoundationCapabilityAwaitingValidation() {
        List<String> others = new ArrayList<>();
        for (JsonNode entry : matrix().get("tested_with")) {
            String capability = text(entry, "capability");
            if (!VALIDATED_ENTRIES.containsKey(capability) && !Capabilities.ALL.contains(capability)) {
                others.add(capability);
            }
        }

        assertThat(others).isEmpty();
        assertThat(matrix().get("tested_with")).hasSize(VALIDATED_ENTRIES.size() + Capabilities.ALL.size());
    }

    private static List<JsonNode> entries(String capability) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode entry : matrix().get("tested_with")) {
            if (capability.equals(text(entry, "capability"))) {
                found.add(entry);
            }
        }
        return found;
    }

    private static JsonNode matrix() {
        try (InputStream in =
                CapabilityMatrixConsistencyTest.class.getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            assertThat(in).as(PecCompatibilityMatrix.RESOURCE).isNotNull();
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + PecCompatibilityMatrix.RESOURCE, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            values.add(value.asString());
        }
        return values;
    }

    private static String sha256(String text) throws Exception {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return "sha256:"
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
