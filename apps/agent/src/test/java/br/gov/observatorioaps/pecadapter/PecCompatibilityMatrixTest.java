package br.gov.observatorioaps.pecadapter;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PecCompatibilityMatrixTest {

    private static final PecSourceIdentity CT133_IDENTITY =
            new PecSourceIdentity("5.4.37", "PEC_DW", "PRONTUARIO");

    @Test
    void loadsTheCompatibilityContractFromTheClasspath() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        var entry = matrix.findExact(
                "individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");
        assertThat(entry.capability()).isEqualTo("individual_encounter_modality");
        assertThat(entry.queryChecksum()).isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
        assertThat(entry.objectFingerprints()).containsKey("tb_dim_tempo");
    }

    @Test
    void emptyMatrixCannotSelectAnAdapter() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromJson(
                "{\"schema_version\":\"1\",\"validation_status\":\"VALIDATED\",\"tested_with\":[]}");

        assertThatThrownBy(() -> matrix.findExact(
                "individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tested_with");
    }

    @Test
    void aWrongVersionOrIdentityCannotSelectTheFirstEntryByAccident() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        assertThatThrownBy(() -> matrix.findExact(
                "individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.14"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");
        assertThatThrownBy(() -> matrix.findExact(
                "individual_encounter_modality", "0.1.0",
                new PecSourceIdentity("5.4.38", "PEC_DW", "PRONTUARIO"), "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");
    }

    @Test
    void compatibilityValidationChecksEveryFingerprintAndTheQueryChecksum() throws Exception {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();
        var expected = matrix.findExact(
                "individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");
        CompatibilityCatalog catalog = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection connection) {
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection connection, String object, java.util.List<String> columnsUsed) {
                return expected.objectFingerprints().get(object);
            }
        };

        IndividualEncounterModalityCapability.validateAdapterCompatibility(
                null, CT133_IDENTITY, catalog, matrix);

        CompatibilityCatalog changedFingerprint = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection connection) {
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection connection, String object, java.util.List<String> columnsUsed) {
                return object.equals("tb_dim_tempo") ? "sha256:changed" : expected.objectFingerprints().get(object);
            }
        };
        assertThatThrownBy(() -> IndividualEncounterModalityCapability.validateAdapterCompatibility(
                null, CT133_IDENTITY, changedFingerprint, matrix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fingerprint");

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.validateAdapterCompatibility(
                null, null, catalog, matrix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PecSourceIdentity");
    }

    @Test
    void aChangedQueryChecksumBlocksTheExactEntry() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(IndividualEncounterModalityCapability.QUERY_CHECKSUM,
                            "sha256:0000000000000000000000000000000000000000000000000000000000000000");
        }
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromJson(json);
        var catalog = CompatibilityTestCatalog.productionEntry();

        assertThatThrownBy(() -> IndividualEncounterModalityCapability.validateAdapterCompatibility(
                null, CT133_IDENTITY, catalog, matrix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Query checksum mismatch");
    }

    @Test
    void compatibilityAwareStreamDoesNotExposeAnIdentityFreeOverload() {
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("stream"))
                .map(java.lang.reflect.Method::getParameterCount))
                .doesNotContain(6);
    }
}
