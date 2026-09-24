package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Reproduces, through the actual production adapter code, the psql/pgJDBC baseline recorded in
 * docs/discovery/2026-09-19-pec-ct133.md for the 2026-03 pilot competency (ADR 0004):
 * 7100 programados / 2929 espontâneos / 0 fora do mapeamento / 10029 total.
 */
class IndividualEncounterModalityCapabilityLiveTest {

    private static final Path ENV_FILE =
            Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env");

    @Test
    void reproducesThe202603BaselineThroughTheFrozenAdapterQuery() throws Exception {
        Assumptions.assumeTrue(Files.exists(ENV_FILE), "Skipping: no dev PEC secret file at " + ENV_FILE);

        Map<String, String> env = readEnvFile();
        var properties = new PecConnectionProperties(
                "pec-ct133-dev",
                env.get("PEC_DB_HOST"),
                Integer.parseInt(env.get("PEC_DB_PORT")),
                env.get("PEC_DB_NAME"),
                env.get("PEC_DB_USER"),
                "PEC_DB_PASSWORD",
                "3541307");
        Assumptions.assumeTrue(
                esusdata.testsupport.LivePecAssumptions.isReachable(properties.host(), properties.port()),
                "Skipping: " + properties.host() + ":" + properties.port() + " not reachable "
                        + "— SSH tunnel likely down (see ADR 0003)");

        var allowlist =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(properties.host(), properties.port())));
        var factory = new PecDataSourceFactory(allowlist, new EnvFileSecretResolver(ENV_FILE));

        PecSourceConnection sourceConnection = factory.open(
                properties,
                new PecSourceIdentity("pec-ct133-dev", "5.4.37", "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal());
        List<RawEncounterRecord> records = new ArrayList<>();
        try (sourceConnection) {
            var acquisition = sourceConnection.acquire(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
            IndividualEncounterModalityCapability.stream(acquisition, records::add);
        }

        long programados = records.stream()
                .filter(r -> r.modality() == EncounterModality.PROGRAMADO)
                .count();
        long espontaneos = records.stream()
                .filter(r -> r.modality() == EncounterModality.ESPONTANEO)
                .count();
        long unmapped = records.stream()
                .filter(r -> r.modality() == EncounterModality.UNMAPPED)
                .count();

        assertThat(records).hasSize(10029);
        assertThat(programados).isEqualTo(7100);
        assertThat(espontaneos).isEqualTo(2929);
        assertThat(unmapped).isZero();

        // Grain: no duplicate (uuidFicha, nuAtendimento) pairs — re-asserted per competency,
        // not assumed from the discovery sample (docs/discovery §Grain).
        long distinctPairs = records.stream()
                .map(r -> r.uuidFicha() + "|" + r.nuAtendimento())
                .distinct()
                .count();
        assertThat(distinctPairs).isEqualTo(10029);
    }

    private static Map<String, String> readEnvFile() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(ENV_FILE)) {
            int i = line.indexOf('=');
            if (i > 0) {
                values.put(line.substring(0, i), line.substring(i + 1));
            }
        }
        return values;
    }
}
