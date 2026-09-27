package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Every way a child's probe can disagree with the matrix is a reported mismatch, never a pass. */
class ExecPlaneProbeVerifierTest {

    private static final String CAPABILITY = "municipal_isolation";
    private static final String CHECKSUM = "sha256:" + "a".repeat(64);
    private static final PecSourceIdentity IDENTITY = new PecSourceIdentity("src", "5.5.28", "PEC_DW", "PRONTUARIO");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String COLUMNS =
            "\"columns\":[{\"name\":\"col_a\",\"data_type\":\"text\",\"udt_name\":\"text\",\"is_nullable\":\"NO\","
                    + "\"ordinal_position\":1}]";

    private static PecCompatibilityMatrix matrix(String columnsUsed, String fingerprint) {
        return matrixOf("{\"object\": \"test_object\", \"signature_fingerprint\": \"" + fingerprint
                + "\", \"columns_used\": " + columnsUsed + "}");
    }

    private static PecCompatibilityMatrix matrixOf(String objectsUsed) {
        return PecCompatibilityMatrix.fromJson("""
                {
                  "schema_version": "2",
                  "validation_status": "VALIDATED",
                  "tested_with": [{
                    "pec_versions": ["5.5.28"],
                    "postgresql_version": "9.6.13",
                    "adapter_version": "0.1.0",
                    "read_model": "PEC_DW",
                    "installation_role": "PRONTUARIO",
                    "capability": "municipal_isolation",
                    "status": "VALIDATED",
                    "query_checksum": "%s",
                    "objects_used": [%s]
                  }]
                }
                """.formatted(CHECKSUM, objectsUsed));
    }

    private static String columnFingerprint() {
        return CompatibilityFingerprint.compute(new CompatibilityProbeResult(
                "test_object",
                Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
                List.of(new ProbeItem.ColumnItem("col_a"))));
    }

    private static JsonNode probe(String postgresVersion, String queryChecksum, String objects) {
        return MAPPER.readTree("{\"type\":\"probe\",\"postgres_version\":\"" + postgresVersion
                + "\",\"query_checksum\":\"" + queryChecksum + "\",\"objects\":" + objects + "}");
    }

    private static String mismatch(PecCompatibilityMatrix matrix, String expectedChecksum, JsonNode probe) {
        return ExecPlaneProbeVerifier.mismatch(matrix, CAPABILITY, "0.1.0", expectedChecksum, IDENTITY, probe);
    }

    @Test
    void aMatchingProbePasses() {
        assertThat(mismatch(
                        matrix("[\"col_a\"]", columnFingerprint()),
                        CHECKSUM,
                        probe("9.6.13", CHECKSUM, "{\"test_object\":{" + COLUMNS + "}}")))
                .isNull();
    }

    @Test
    void anUnlistedPostgresVersionHasNoEntry() {
        assertThat(mismatch(matrix("[\"col_a\"]", columnFingerprint()), CHECKSUM, probe("16.1", CHECKSUM, "{}")))
                .startsWith("no compatibility matrix entry");
    }

    @Test
    void theMatrixAndThePackagedQueryMustAgree() {
        assertThat(mismatch(
                        matrix("[\"col_a\"]", columnFingerprint()),
                        "sha256:" + "b".repeat(64),
                        probe("9.6.13", CHECKSUM, "{}")))
                .isEqualTo("query checksum mismatch: matrix has " + CHECKSUM);
    }

    @Test
    void theChildMustRunTheFrozenQuery() {
        assertThat(mismatch(matrix("[\"col_a\"]", columnFingerprint()), CHECKSUM, probe("9.6.13", "sha256:x", "{}")))
                .contains("but execution plane reported sha256:x");
    }

    @Test
    void anObjectTheChildDidNotProbeIsAMismatch() {
        assertThat(mismatch(matrix("[\"col_a\"]", columnFingerprint()), CHECKSUM, probe("9.6.13", CHECKSUM, "{}")))
                .isEqualTo("no probe data reported for object test_object");
    }

    @Test
    void aDifferentColumnTypeIsAFingerprintMismatch() {
        assertThat(mismatch(
                        matrix("[\"col_a\"]", columnFingerprint()),
                        CHECKSUM,
                        probe(
                                "9.6.13",
                                CHECKSUM,
                                "{\"test_object\":{" + COLUMNS.replace("\"text\"", "\"varchar\"") + "}}")))
                .startsWith("fingerprint mismatch for test_object");
    }

    @Test
    void omittedRequiredDimensionsEvidenceIsNeverReadAsCoverage() {
        String fact = "{\"object\": \"tb_fat_atendimento_individual\", \"signature_fingerprint\": \"sha256:"
                + "0".repeat(64) + "\", \"columns_used\": [\"REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio\"]}";
        assertThat(mismatch(
                        matrixOf(fact),
                        CHECKSUM,
                        probe("9.6.13", CHECKSUM, "{\"tb_fat_atendimento_individual\":{" + COLUMNS + "}}")))
                .startsWith("could not compute fingerprint for tb_fat_atendimento_individual");
    }

    @Test
    void everyMarkerKindIsReadFromTheProbe() {
        String zero = "sha256:" + "0".repeat(64);
        String objectsUsed = "{\"object\": \"tb_fat_atendimento_individual\", \"signature_fingerprint\": \""
                + zero + "\", \"columns_used\": [\"col_a\", \"UNIQUE_KEY=col_a\","
                + " \"REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio\"]},"
                + " {\"object\": \"tb_dim_tipo_atendimento\", \"signature_fingerprint\": \"" + zero
                + "\", \"columns_used\": [\"LEAF_SEMANTICS=2\", \"LEAF_IDS=2\"]}";
        String objects = "{\"tb_fat_atendimento_individual\":{" + COLUMNS
                + ",\"unique_key\":{\"matched_constraint_type\":\"PRIMARY KEY\",\"uniqueness_violation_found\":false}"
                + ",\"required_dimensions\":{\"violating_fact_event_id\":null}},"
                + "\"tb_dim_tipo_atendimento\":{" + COLUMNS
                + ",\"leaf_semantics\":{\"rows\":[{\"id\":2,\"description\":\"Consulta\",\"parent_id\":1}]}"
                + ",\"leaf_ids\":{\"found_ids\":[2]}}}";

        String result = mismatch(matrixOf(objectsUsed), CHECKSUM, probe("9.6.13", CHECKSUM, objects));

        // Each fingerprint is computed from all its markers; only its value differs from the pinned
        // one. Which object is compared first depends on the matrix's map order.
        assertThat(result).startsWith("fingerprint mismatch for tb_").contains("but computed sha256:");
    }
}
