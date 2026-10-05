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
 * ENG-43 guard of the foundation capabilities (ADR 0030): each one has exactly one {@code VALIDATED}
 * entry in {@code contracts/compatibility/pec-adapters.json}, approved on 2026-10-05 against the
 * production PEC 5.5.28 ({@code docs/discovery/2026-10-05-pec-5528-capacidades.md}), pinned to the
 * query that runs and to the synthetic fixture it was tested on, listing exactly the tables and
 * columns the query reads — and every approved entry, C1's, isolation's and coverage's included,
 * stays byte-for-byte what was approved. Editing a query or the fixture without a new validation
 * fails here, not in review.
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

    /** The same digest for each foundation capability, approved with the 2026-10-05 live evidence. */
    private static final Map<String, String> FOUNDATION_APPROVALS = Map.of(
            "citizen",
            "sha256:1ff10b887c2ab2000ed68eae24a2d1f179e79caf66b0ad5ac44cea5552ecb64c",
            "individual_registration",
            "sha256:14cbe92ae33d2e207bf8c49e6cbe91ce7d0a52d90f36998a60ff61edb9673f3e",
            "care_encounter",
            "sha256:ad7cce53e51eb4dc85024c9a3f13ed7dde90be4437c354b68eb21c769ace2358",
            "dental_encounter",
            "sha256:c3beae2f9496b35d9a9d8feb33842d571cce9dd3e5b4d5a9c5c2d333bcebb029",
            "home_visit",
            "sha256:1f8f26f676643dfadf0cd6ad76f1153e6173ceeb15fcbfa87117540b65ea96a6",
            "immunization_history",
            "sha256:14ec88b3658a241c81e47de9cd7badb42f101f265e55a1b5fc689d764aa6e7ed",
            "exam_request_evaluation",
            "sha256:ae8f5f5c7b2f576b60c45355f2873651c9923542a16db3375d00e8c8c218507d",
            "procedure_performed",
            "sha256:eae58f8c2027153c0f519e41a8ad4e7377cbe5b5164400ae381e0f3c6d005f29",
            "condition_list",
            "sha256:4c7f3ddeefe771492584e18c1e69912e8a05175a965e9c8fa8726a10cae5b0e1",
            "measurement_record",
            "sha256:e3e8fe9eaa305d155ca84c91cf83ef8df80a942a80e0da980c08799df14b49bb");

    static Stream<String> foundation() {
        return Capabilities.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyFoundationCapabilityHasOneApprovedEntryPinnedToItsQueryAndFixture(String capability) throws Exception {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        List<JsonNode> entries = entries(capability);

        assertThat(entries).as(capability).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(text(entry, "status")).isEqualTo("VALIDATED");
        assertThat(text(entry, "test_result")).isEqualTo("PASS");
        assertThat(text(entry, "approved_at")).isEqualTo("2026-10-05");
        assertThat(text(entry, "approved_by")).isNotBlank();
        assertThat(sha256(MAPPER.writeValueAsString(entry)))
                .as(capability + " was approved with ADR 0023 evidence; a change is a new validation")
                .isEqualTo(FOUNDATION_APPROVALS.get(capability));
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
    void everyEntryIsApprovedAndPinned() {
        List<String> others = new ArrayList<>();
        for (JsonNode entry : matrix().get("tested_with")) {
            String capability = text(entry, "capability");
            if (!VALIDATED_ENTRIES.containsKey(capability) && !Capabilities.ALL.contains(capability)) {
                others.add(capability);
            }
        }

        assertThat(others).isEmpty();
        assertThat(FOUNDATION_APPROVALS.keySet()).containsExactlyInAnyOrderElementsOf(Capabilities.ALL);
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
