package br.gov.observatorioaps.pecadapter;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-43 guard: the frozen {@code query_checksum} recorded in
 * {@code contracts/compatibility/pec-adapters.json} must always equal
 * {@link IndividualEncounterModalityCapability#QUERY_CHECKSUM} computed from the query that
 * actually runs. If someone edits the SQL without updating the matrix, this test — not a human
 * reviewer — is what catches it.
 */
class PecAdaptersMatrixConsistencyTest {

    @Test
    void frozenQueryChecksumMatchesTheLiveAdapterQuery() throws IOException {
        Path matrixFile = repoRoot().resolve("contracts/compatibility/pec-adapters.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(matrixFile));
        JsonNode entry = root.get("tested_with").get(0);

        assertThat(entry.get("capability").asString()).isEqualTo("individual_encounter_modality");
        assertThat(entry.get("query_checksum").asString())
                .as("contracts/compatibility/pec-adapters.json query_checksum must match "
                        + "IndividualEncounterModalityCapability.QUERY_CHECKSUM — update the matrix "
                        + "if the adapter query changed intentionally")
                .isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
    }

    private Path repoRoot() {
        // apps/agent -> repo root
        return Path.of("").toAbsolutePath().getParent().getParent();
    }
}
