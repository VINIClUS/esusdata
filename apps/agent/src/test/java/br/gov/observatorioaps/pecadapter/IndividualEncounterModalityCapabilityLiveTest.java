package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.BudgetGuard;
import br.gov.observatorioaps.sourceconnector.EnvFileSecretResolver;
import br.gov.observatorioaps.sourceconnector.PecConnectionProperties;
import br.gov.observatorioaps.sourceconnector.PecDataSourceFactory;
import br.gov.observatorioaps.sourceconnector.ReadBudget;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        Assumptions.assumeTrue(Files.exists(ENV_FILE),
                "Skipping: no dev PEC secret file at " + ENV_FILE);

        Map<String, String> env = readEnvFile();
        var properties = new PecConnectionProperties(
                "pec-ct133-dev",
                env.get("PEC_DB_HOST"),
                Integer.parseInt(env.get("PEC_DB_PORT")),
                env.get("PEC_DB_NAME"),
                env.get("PEC_DB_USER"),
                "PEC_DB_PASSWORD",
                "3541307"
        );
        var allowlist = new AllowedDestinations(
                Set.of(new AllowedDestinations.HostPort(properties.host(), properties.port())));
        var factory = new PecDataSourceFactory(allowlist, new EnvFileSecretResolver(ENV_FILE));

        HikariDataSource ds = factory.create(properties, ReadBudget.initialEngineeringProposal());
        List<RawEncounterRecord> records = new ArrayList<>();
        try {
            try (Connection c = ds.getConnection()) {
                var guard = new BudgetGuard(ReadBudget.initialEngineeringProposal());
                IndividualEncounterModalityCapability.stream(
                        c, "3541307",
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                        guard, records::add);
            }
        } finally {
            ds.close();
        }

        long programados = records.stream().filter(r -> r.modality() == EncounterModality.PROGRAMADO).count();
        long espontaneos = records.stream().filter(r -> r.modality() == EncounterModality.ESPONTANEO).count();
        long unmapped = records.stream().filter(r -> r.modality() == EncounterModality.UNMAPPED).count();

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

    private Map<String, String> readEnvFile() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(ENV_FILE)) {
            int i = line.indexOf('=');
            if (i > 0) values.put(line.substring(0, i), line.substring(i + 1));
        }
        return values;
    }
}
