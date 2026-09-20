package br.gov.observatorioaps.sourceconnector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.net.InetAddress;
import java.time.Duration;

/**
 * The only place in this codebase that assembles a PEC JDBC URL string — always from
 * {@link PecConnectionProperties}' structured fields, never from a caller-supplied URL
 * (§1.12.6 SSRF control). {@link AllowedDestinations} is checked before the pool is created.
 *
 * <p>Every physical connection this pool ever hands out has already run, once, at creation:
 * {@code application_name=observatorio-aps}, {@code default_transaction_read_only=on}, and the
 * three PostgreSQL session-level budget GUCs from {@link ReadBudget} — via HikariCP's
 * {@code connectionInitSql}, which runs on every new physical connection, not just the first one
 * a given logical caller happens to borrow.
 */
public final class PecDataSourceFactory {

    private final AllowedDestinations allowedDestinations;
    private final PecSecretResolver secretResolver;

    public PecDataSourceFactory(AllowedDestinations allowedDestinations, PecSecretResolver secretResolver) {
        this.allowedDestinations = allowedDestinations;
        this.secretResolver = secretResolver;
    }

    public HikariDataSource create(PecConnectionProperties properties, ReadBudget budget) {
        InetAddress validatedAddress = allowedDestinations.assertAllowed(properties.host(), properties.port());

        String jdbcUrl = "jdbc:postgresql://" + jdbcHostLiteral(validatedAddress) + ":" + properties.port()
                + "/" + properties.database();

        char[] password = secretResolver.resolve(properties.secretRef());
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(properties.user());
            config.setPassword(new String(password));
            config.setReadOnly(true);
            config.setAutoCommit(false);
            config.setMaximumPoolSize(budget.poolMaxSize());
            config.setMinimumIdle(0);
            // Configuration creation must not perform an unbounded/fail-fast network attempt;
            // the acquisition timeout applies when a caller actually borrows a connection.
            config.setInitializationFailTimeout(-1);
            config.setConnectionTimeout(budget.acquisitionTimeout().toMillis());
            config.addDataSourceProperty("connectTimeout", pgConnectTimeoutSeconds(budget.connectionTimeout()));
            config.addDataSourceProperty("socketTimeout", pgTimeoutSeconds(
                    budget.maxDurationMs(), "maxDurationMs"));
            config.addDataSourceProperty("cancelSignalTimeout", pgTimeoutSeconds(
                    budget.connectionTimeout().toMillis(), "connectionTimeout"));
            config.addDataSourceProperty("queryTimeout", pgTimeoutSeconds(
                    budget.statementTimeoutMs(), "statementTimeoutMs"));
            config.setPoolName("pec-" + properties.sourceId());
            config.setConnectionInitSql(
                    "SET application_name = 'observatorio-aps'; "
                            + "SET default_transaction_read_only = on; "
                            + "SET statement_timeout = " + budget.statementTimeoutMs() + "; "
                            + "SET lock_timeout = " + budget.lockTimeoutMs() + "; "
                            + "SET idle_in_transaction_session_timeout = " + budget.idleInTransactionTimeoutMs() + ";"
            );
            return new HikariDataSource(config);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static String jdbcHostLiteral(InetAddress address) {
        String literal = address.getHostAddress();
        return address.getAddress().length == 16 ? "[" + literal + "]" : literal;
    }

    private static int pgConnectTimeoutSeconds(Duration timeout) {
        return pgTimeoutSeconds(timeout.toMillis(), "connectionTimeout");
    }

    private static int pgTimeoutSeconds(long millis, String setting) {
        if (millis <= 0) {
            throw new IllegalArgumentException(setting + " must be positive");
        }
        long seconds = millis / 1000;
        if (millis % 1000 != 0) seconds++;
        seconds = Math.max(1, seconds);
        if (seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(setting + " is too large for pgJDBC timeout properties");
        }
        return (int) seconds;
    }
}
