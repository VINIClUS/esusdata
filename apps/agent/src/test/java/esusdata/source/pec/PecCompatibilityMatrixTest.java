package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PecCompatibilityMatrixTest {

    private static final PecSourceIdentity CT133_IDENTITY =
            new PecSourceIdentity("matrix-test", "5.4.37", "PEC_DW", "PRONTUARIO");

    @Test
    void loadsTheCompatibilityContractFromTheClasspath() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        var entry = matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");
        assertThat(entry.capability()).isEqualTo("individual_encounter_modality");
        assertThat(entry.queryChecksum()).isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
        assertThat(entry.objectFingerprints()).containsKey("tb_dim_tempo");
    }

    @Test
    void compatibilityContractFingerprintsEveryDimensionJoinedByTheCapability() {
        var entry = PecCompatibilityMatrix.fromClasspathResource()
                .findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");

        assertThat(entry.objectFingerprints())
                .containsKeys(
                        "tb_fat_atendimento_individual",
                        "tb_dim_tempo",
                        "tb_dim_municipio",
                        "tb_dim_tipo_atendimento",
                        "tb_dim_unidade_saude",
                        "tb_dim_equipe",
                        "tb_dim_cbo");
        assertThat(entry.objectColumns().get("tb_fat_atendimento_individual"))
                .contains("UNIQUE_KEY=co_seq_fat_atd_ind", "REQUIRED_DIMENSIONS=tb_dim_tempo,tb_dim_municipio");
        assertThat(entry.objectColumns().get("tb_dim_unidade_saude"))
                .contains("nu_cnes", "UNIQUE_KEY=co_seq_dim_unidade_saude");
        assertThat(entry.objectColumns().get("tb_dim_equipe")).contains("nu_ine", "UNIQUE_KEY=co_seq_dim_equipe");
        assertThat(entry.objectColumns().get("tb_dim_cbo")).contains("nu_cbo", "UNIQUE_KEY=co_seq_dim_cbo");
    }

    @Test
    void frozenAcquisitionQueryUsesTheSchemaFingerprintedByTheCompatibilityCatalog() {
        assertThat(IndividualEncounterModalityCapability.QUERY)
                .contains("FROM public.tb_fat_atendimento_individual")
                .contains("JOIN public.tb_dim_tempo")
                .contains("JOIN public.tb_dim_municipio")
                .contains("LEFT JOIN public.tb_dim_unidade_saude")
                .contains("LEFT JOIN public.tb_dim_equipe")
                .contains("LEFT JOIN public.tb_dim_cbo");
    }

    @Test
    void emptyMatrixCannotSelectAnAdapter() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromJson(
                "{\"schema_version\":\"2\",\"validation_status\":\"VALIDATED\",\"tested_with\":[]}");

        assertThatThrownBy(() -> matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tested_with");
    }

    @Test
    void aWrongVersionOrIdentityCannotSelectTheFirstEntryByAccident() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        assertThatThrownBy(() -> matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.14"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");
        assertThatThrownBy(() -> matrix.findExact(
                        "individual_encounter_modality",
                        "0.1.0",
                        new PecSourceIdentity("matrix-test", "5.4.38", "PEC_DW", "PRONTUARIO"),
                        "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");
    }

    @Test
    void everyListedPecVersionSelectsTheSameEntry() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        var validatedOnCt133 = matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");
        var validatedOn5528 = matrix.findExact(
                "individual_encounter_modality",
                "0.1.0",
                new PecSourceIdentity("matrix-test", "5.5.28", "PEC_DW", "PRONTUARIO"),
                "9.6.13");

        assertThat(validatedOn5528).isEqualTo(validatedOnCt133);
        assertThat(validatedOn5528.pecVersions()).containsExactly("5.4.37", "5.5.28");
    }

    @Test
    void aVersionBetweenTwoListedOnesIsNotARange() {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();

        assertThatThrownBy(() -> matrix.findExact(
                        "individual_encounter_modality",
                        "0.1.0",
                        new PecSourceIdentity("matrix-test", "5.5.0", "PEC_DW", "PRONTUARIO"),
                        "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact compatibility entry");
    }

    @Test
    void aSchemaVersion1MatrixWithAScalarPecVersionIsRejected() {
        PecCompatibilityMatrix matrix =
                PecCompatibilityMatrix.fromJson("{\"schema_version\":\"1\",\"validation_status\":\"VALIDATED\","
                        + "\"tested_with\":[{\"pec_version\":\"5.4.37\"}]}");

        assertThatThrownBy(() -> matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported compatibility matrix schema");
    }

    @Test
    void compatibilityValidationChecksEveryFingerprintAndTheQueryChecksum() throws Exception {
        PecCompatibilityMatrix matrix = PecCompatibilityMatrix.fromClasspathResource();
        var expected = matrix.findExact("individual_encounter_modality", "0.1.0", CT133_IDENTITY, "9.6.13");
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

        IndividualEncounterModalityCapability.validateAdapterCompatibility(null, CT133_IDENTITY, catalog, matrix);

        CompatibilityCatalog changedFingerprint = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection connection) {
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection connection, String object, java.util.List<String> columnsUsed) {
                return "tb_dim_tempo".equals(object)
                        ? "sha256:changed"
                        : expected.objectFingerprints().get(object);
            }
        };
        assertThatThrownBy(() -> IndividualEncounterModalityCapability.validateAdapterCompatibility(
                        null, CT133_IDENTITY, changedFingerprint, matrix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fingerprint");

        assertThatThrownBy(() ->
                        IndividualEncounterModalityCapability.validateAdapterCompatibility(null, null, catalog, matrix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PecSourceIdentity");
    }

    @Test
    void aChangedQueryChecksumBlocksTheExactEntry() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(
                            IndividualEncounterModalityCapability.QUERY_CHECKSUM,
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
    void compatibilityAwareStreamRequiresAConnectionBoundSourceIdentity() {
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                        .filter(method -> "stream".equals(method.getName()))
                        .allMatch(method -> method.getParameterTypes()[0].equals(PecAcquisition.class)))
                .isTrue();
    }

    @Test
    void rawCompatibilityValidationIsNotAPublicUnboundAcquisitionApi() {
        assertThat(Arrays.stream(IndividualEncounterModalityCapability.class.getDeclaredMethods())
                        .filter(method -> "validateAdapterCompatibility".equals(method.getName()))
                        .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                                && method.getParameterTypes().length > 1
                                && method.getParameterTypes()[0].equals(Connection.class)
                                && method.getParameterTypes()[1].equals(PecSourceIdentity.class)))
                .isTrue();
    }
}
