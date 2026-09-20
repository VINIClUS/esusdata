package br.gov.observatorioaps.sourceconnector;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test against the real e-SUS PEC on CT 133, reached through the SSH tunnel
 * documented in ADR 0003. Skips itself (does not fail the build) when the dev secret file is
 * absent — CI and other machines never have it — so this is opt-in evidence, not a hard gate.
 *
 * <p>This is the same connection path the discovery spike proved manually; here it is exercised
 * through the actual production classes ({@link PecDataSourceFactory}, {@link AllowedDestinations},
 * {@link ReadBudget}) instead of throwaway spike code.
 */
class PecDataSourceFactoryLiveTest {

    private static final Path ENV_FILE =
            Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env");

    @Test
    void connectsThroughTheTunnelAndAppliesTheReadBudget() throws Exception {
        Assumptions.assumeTrue(Files.exists(ENV_FILE),
                "Skipping: no dev PEC secret file at " + ENV_FILE + " (expected outside CI)");

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

        Assumptions.assumeTrue(
                br.gov.observatorioaps.testsupport.LivePecAssumptions.isReachable(properties.host(), properties.port()),
                "Skipping: " + properties.host() + ":" + properties.port() + " not reachable "
                        + "— SSH tunnel likely down (see ADR 0003)");

        var allowlist = new AllowedDestinations(
                Set.of(new AllowedDestinations.HostPort(properties.host(), properties.port())));
        var factory = new PecDataSourceFactory(allowlist, new EnvFileSecretResolver(ENV_FILE));

        HikariDataSource ds = factory.create(properties, ReadBudget.initialEngineeringProposal());
        try {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select current_user, version()")) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo("esus_leitura");
                    assertThat(rs.getString(2)).contains("PostgreSQL 9.6.13");
                }
                // The budget GUCs applied via connectionInitSql must be visible on this connection.
                try (ResultSet rs = st.executeQuery(
                        "select current_setting('statement_timeout'), "
                                + "current_setting('default_transaction_read_only'), "
                                + "current_setting('application_name')")) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo("30s");
                    assertThat(rs.getString(2)).isEqualTo("on");
                    assertThat(rs.getString(3)).isEqualTo("observatorio-aps");
                }
                // The credential must not be able to write, even though the app also forces
                // read-only at the connection/session level — defense in depth, not the only line.
                try (ResultSet rs = st.executeQuery(
                        "select has_table_privilege('esus_leitura', 'tb_fat_atendimento_individual', 'INSERT')")) {
                    rs.next();
                    assertThat(rs.getBoolean(1)).isFalse();
                }
            }
        } finally {
            ds.close();
        }
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
