package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ENG-43 guard: the frozen {@code query_checksum} recorded in
 * {@code contracts/compatibility/pec-adapters.json} must always equal
 * {@link IndividualEncounterModalityCapability#QUERY_CHECKSUM} computed from the query that
 * actually runs. If someone edits the SQL without updating the matrix, this test — not a human
 * reviewer — is what catches it.
 */
class PecAdaptersMatrixConsistencyTest {

    @ParameterizedTest
    @ValueSource(strings = {"5.4.37", "5.5.28"})
    void frozenQueryChecksumMatchesTheLiveAdapterQuery(String pecVersion) {
        var entry = PecCompatibilityMatrix.fromClasspathResource()
                .findExact(
                        IndividualEncounterModalityCapability.CAPABILITY,
                        IndividualEncounterModalityCapability.ADAPTER_VERSION,
                        new PecSourceIdentity("matrix-test", pecVersion, "PEC_DW", "PRONTUARIO"),
                        "9.6.13");

        assertThat(entry.capability()).isEqualTo("individual_encounter_modality");
        assertThat(entry.queryChecksum())
                .as("contracts/compatibility/pec-adapters.json query_checksum must match "
                        + "IndividualEncounterModalityCapability.QUERY_CHECKSUM — update the matrix "
                        + "if the adapter query changed intentionally")
                .isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
    }
}
