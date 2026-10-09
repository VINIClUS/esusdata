package esusdata.run.worker;

import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.MunicipalIsolationContract;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.ReadBudget;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The read-only handshake with the PEC that comes before any computation of the diagnostic (ADR
 * 0017: pgJDBC is a test-scoped dependency, never the product's). It connects as the secret file
 * says, with the session read-only from the login on, proves that it is, records the PostgreSQL
 * version, checks that the registered municipality is in this PEC, rolls back and closes the
 * connection, and only then hands back the {@link SourceIdentity} the extract partitions are keyed
 * by. The acquisitions of the diagnostic run through the execution plane afterwards; none of them
 * ever goes through this connection.
 *
 * <p>What it reads, and nothing else: {@code SHOW transaction_read_only}, {@code SHOW
 * server_version} and, for the municipality, the frozen {@code municipal_isolation} aggregate
 * ({@link MunicipalIsolationContract#QUERY}: the atendimentos of one month counted per {@code
 * co_ibge}, no row of the PEC) over the months the caller names, stopping at the first in which the
 * registered municipality has any. That is the existing read-only mechanism of the product for "is
 * this municipality in this source"; it is run here, in the one transaction, because the preflight
 * must prove the municipality before the execution plane is spawned. The months are the source's
 * recent ones ({@link #recentMonths}), never the periods a run asks for: the identity of the source
 * does not depend on them, and a period the source lacks is a row of the diagnostic that names its
 * missing months, not a refused source.
 *
 * <p>The password is resolved at the point of use, copied into the login properties only, and
 * zeroed; it is in no URL, no message and no log.
 */
public final class ReadOnlyPecPreflight {

    /** Read-only before the first statement, with the engineering statement budget as its timeout. */
    static final String OPTIONS = "-c default_transaction_read_only=on -c statement_timeout="
            + ReadBudget.initialEngineeringProposal().statementTimeoutMs();

    static final String APPLICATION_NAME = "observatorio-aps-portao-d-preflight";

    static final String SHOW_READ_ONLY = "SHOW transaction_read_only";

    static final String SHOW_VERSION = "SHOW server_version";

    private static final String HOST_KEY = "PEC_DB_HOST";
    private static final String PORT_KEY = "PEC_DB_PORT";
    private static final String NAME_KEY = "PEC_DB_NAME";
    private static final String USER_KEY = "PEC_DB_USER";
    private static final String SOURCE_ID_KEY = "PEC_SOURCE_ID";
    private static final String VERSION_KEY = "PEC_VERSION";
    private static final String MUNICIPALITY_KEY = "PEC_MUNICIPALITY_IBGE";
    private static final String ON = "on";
    private static final String SECONDS_TO_CONNECT = "5";
    private static final String SECONDS_TO_ANSWER = "60";

    private final Connector connector;
    private final PecSecretResolver secrets;

    /**
     * A preflight that opens its one connection with {@code connector}.
     *
     * @param connector how a connection is opened; {@code DriverManager::getConnection} when live
     * @param secrets where the password is resolved from, at the point of use
     */
    public ReadOnlyPecPreflight(Connector connector, PecSecretResolver secrets) {
        this.connector = connector;
        this.secrets = secrets;
    }

    /**
     * The months to look for the municipality in: {@code count} months back from {@code current},
     * the newest first, where a source in use is most likely to have atendimentos.
     */
    public static List<YearMonth> recentMonths(YearMonth current, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("look in at least one month");
        }
        List<YearMonth> months = new ArrayList<>();
        for (int back = 0; back < count; back++) {
            months.add(current.minusMonths(back));
        }
        return List.copyOf(months);
    }

    /** The preflight of the PEC the secret file describes. */
    public static ReadOnlyPecPreflight live(Path envFile) {
        return new ReadOnlyPecPreflight(DriverManager::getConnection, new EnvFileSecretResolver(envFile));
    }

    /**
     * Connects, checks, rolls back, closes and only then returns.
     *
     * @param environment the {@code PEC_*} entries of the secret file without the password, from
     *     {@link AcquisitionInputs#environmentOf}
     * @param probeMonths the months to look for the registered municipality in, in the order to try
     * @return the identity of the source: its id and version from the secret file, the PostgreSQL
     *     version the session reported, the municipality and the read model
     * @throws IllegalStateException when the session is not read-only (nothing is read after that
     *     check), or when the registered municipality has no atendimento in any probed month
     * @throws SQLException when the PEC cannot be reached or answers an error
     */
    public SourceIdentity run(Map<String, String> environment, List<YearMonth> probeMonths) throws SQLException {
        if (probeMonths.isEmpty()) {
            throw new IllegalArgumentException("name at least one month to look for the municipality in");
        }
        String municipality = required(environment, MUNICIPALITY_KEY);
        String sourceId = required(environment, SOURCE_ID_KEY);
        String pecVersion = required(environment, VERSION_KEY);
        String url = url(environment);
        Properties login = loginOf(environment);

        Observation observation;
        try (Connection connection = connector.open(url, login)) {
            observation = observeAndRollBack(connection, municipality, probeMonths);
        }
        // The connection is closed here; nothing below touches the PEC.
        if (!observation.municipalityFound()) {
            throw new IllegalStateException("the registered municipality has no atendimento in any of the "
                    + probeMonths.size() + " months probed: this PEC is not the source that was registered");
        }
        return new SourceIdentity(
                sourceId, pecVersion, observation.serverVersion(), municipality, AcquisitionInputs.READ_MODEL);
    }

    /** {@code jdbc:postgresql://host:port/database}: where, and no credential. */
    static String url(Map<String, String> environment) {
        return "jdbc:postgresql://" + required(environment, HOST_KEY) + ":" + required(environment, PORT_KEY) + "/"
                + required(environment, NAME_KEY);
    }

    /**
     * The login properties: read-only from the connection on, the session options, the user and the
     * password. Callers must not print them.
     */
    static Properties properties(char[] password, Map<String, String> environment) {
        Properties properties = new Properties();
        properties.setProperty("user", required(environment, USER_KEY));
        properties.setProperty("password", new String(password));
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        properties.setProperty("readOnly", "true");
        properties.setProperty("options", OPTIONS);
        properties.setProperty("loginTimeout", SECONDS_TO_CONNECT);
        properties.setProperty("connectTimeout", SECONDS_TO_CONNECT);
        properties.setProperty("socketTimeout", SECONDS_TO_ANSWER);
        return properties;
    }

    private Properties loginOf(Map<String, String> environment) {
        char[] password = secrets.resolve(AcquisitionInputs.PASSWORD_KEY);
        try {
            return properties(password, environment);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Observation observeAndRollBack(
            Connection connection, String municipality, List<YearMonth> probeMonths) throws SQLException {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        try {
            String readOnly = show(connection, SHOW_READ_ONLY);
            if (!ON.equals(readOnly)) {
                throw new IllegalStateException(
                        "the session is not read-only (transaction_read_only is not on): nothing is read");
            }
            String serverVersion = show(connection, SHOW_VERSION);
            return new Observation(serverVersion, municipalityIsIn(connection, municipality, probeMonths));
        } finally {
            connection.rollback();
        }
    }

    private static String show(Connection connection, String statement) throws SQLException {
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery(statement)) {
            String value = result.next() ? result.getString(1) : null;
            if (value == null || value.isBlank()) {
                throw new SQLException("the PEC answered no value to a SHOW");
            }
            return value.strip();
        }
    }

    /** True at the first probed month the municipality has any atendimento in; later months are not read. */
    private static boolean municipalityIsIn(Connection connection, String municipality, List<YearMonth> probeMonths)
            throws SQLException {
        for (YearMonth month : probeMonths) {
            if (hasAtendimentos(connection, municipality, month)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAtendimentos(Connection connection, String municipality, YearMonth month)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(MunicipalIsolationContract.QUERY)) {
            query.setObject(1, month.atDay(1));
            query.setObject(2, month.plusMonths(1).atDay(1));
            try (ResultSet counts = query.executeQuery()) {
                while (counts.next()) {
                    String ibge = counts.getString(1);
                    if (ibge != null && municipality.equals(ibge.strip()) && counts.getLong(2) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("the secret file has no " + key);
        }
        return value;
    }

    /** How a JDBC connection is opened: {@code DriverManager::getConnection}, or a fake in a test. */
    @FunctionalInterface
    public interface Connector {

        Connection open(String url, Properties properties) throws SQLException;
    }

    private record Observation(String serverVersion, boolean municipalityFound) {}
}
