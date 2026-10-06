package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read-only live session the opt-in inventory tests share ({@link DwInventoryLiveTest},
 * {@link TeamInventoryLiveTest}): the gate, the login and the transaction, so both run a script
 * under the same conditions and the same {@link ReadBudget}.
 *
 * <p><b>Gate:</b> the same as {@code ExecPlaneLivePecTest}: the opt-in {@code
 * -Dobservatorio.execution-plane.live-pec=true}, the secret file ({@link LivePecAssumptions#ENV_FILE}
 * unless {@code -Dobservatorio.execution-plane.live-pec.env-file} points elsewhere), a reachable
 * tunnel and a real login. The execution-plane binary is not needed. Without the opt-in the caller
 * is skipped, so no ordinary build ever reads the production PEC.
 *
 * <p>The scripts come from the classpath copy of {@code contracts/compatibility} (ADR 0009), so the
 * psql path and this one run the same queries. The script's BEGIN, SET and ROLLBACK belong to psql:
 * this class opens its own transaction with {@code SET TRANSACTION ... READ ONLY} and the {@link
 * ReadBudget#initialEngineeringProposal()} timeouts, the same values the scripts set, and always
 * rolls back. The log carries counts and the output path, never a value.
 */
final class LivePecInventory {

    private static final Logger log = LoggerFactory.getLogger(LivePecInventory.class);

    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";
    private static final String HOST_KEY = "PEC_DB_HOST";
    private static final String APPLICATION_NAME = "observatorio-aps-inventario";
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Path envFile;
    private final Map<String, String> env;

    private LivePecInventory(Path envFile, Map<String, String> env) {
        this.envFile = envFile;
        this.env = env;
    }

    /** Skips the calling test (JUnit assumption) unless every part of the gate holds. */
    static LivePecInventory assumeAvailable() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: reads the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        String configuredEnvFile = System.getProperty(ENV_FILE_PROPERTY);
        Path envFile = configuredEnvFile == null || configuredEnvFile.isBlank()
                ? LivePecAssumptions.ENV_FILE
                : Path.of(configuredEnvFile);
        Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
        Map<String, String> env = Files.readAllLines(envFile).stream()
                .filter(line -> line.contains("=") && !line.strip().startsWith("#"))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim(),
                        (first, last) -> last));
        for (String key : List.of(HOST_KEY, "PEC_DB_PORT", "PEC_DB_NAME", "PEC_DB_USER")) {
            Assumptions.assumeTrue(
                    env.get(key) != null && !env.get(key).isBlank(), "Skipping: " + envFile + " has no " + key);
        }
        LivePecInventory session = new LivePecInventory(envFile, env);
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(env.get(HOST_KEY), session.port()),
                "Skipping: " + env.get(HOST_KEY) + ":" + session.port() + " not reachable — tunnel likely down");
        // The tunnel's local port accepts TCP even when the PEC's PostgreSQL behind it is down —
        // only a real login proves there is a server to read.
        Assumptions.assumeTrue(session.canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
        return session;
    }

    /**
     * Runs {@code scriptResource} in one read-only transaction and writes what it prints to
     * {@code target/<directory>/<prefix>-<source id>-<UTC instant>.txt} (git-ignored).
     *
     * @return the output file
     */
    Path run(
            String scriptResource, String directory, String prefix, String title, PsqlScriptRunner.StatementGuard guard)
            throws IOException, SQLException, URISyntaxException, NoSuchAlgorithmException {
        byte[] script = readScript(scriptResource);
        List<PsqlScriptRunner.ScriptStep> steps = PsqlScriptRunner.parse(new String(script, StandardCharsets.UTF_8));
        Path output = outputDirectory(directory).resolve(outputName(prefix));
        Files.createDirectories(output.getParent());

        try (Connection connection = open();
                PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            beginReadOnlyTransaction(connection);
            try {
                assertThat(setting(connection, "transaction_read_only")).isEqualTo("on");
                out.println("-- " + title + ", " + Instant.now());
                out.println("-- " + scriptResource + " sha256:" + sha256(script));
                PsqlScriptRunner.Runner runner = new PsqlScriptRunner.Runner(connection, out, guard);
                for (PsqlScriptRunner.ScriptStep step : steps) {
                    runner.run(step);
                }
                assertThat(setting(connection, "transaction_read_only")).isEqualTo("on");
                assertThat(runner.queries()).isPositive();
                assertThat(out.checkError()).as("writing " + output).isFalse();
                log.info(
                        "{}: {} queries, {} of them generated by \\gexec, written to {}",
                        prefix,
                        runner.queries(),
                        runner.generatedQueries(),
                        output);
            } finally {
                connection.rollback();
            }
        }
        assertThat(output).isNotEmptyFile();
        return output;
    }

    // javac's try lint: the resource is held for the block's scope, never read.
    @SuppressWarnings("try")
    private boolean canLogIn() {
        try (Connection ignored = open()) {
            return true;
        } catch (SQLException unreachable) {
            return false;
        }
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", env.get("PEC_DB_USER"));
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        properties.setProperty("readOnly", "true");
        properties.setProperty("loginTimeout", "5");
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "120");
        // Read-only from the login on, before any statement of this test.
        properties.setProperty("options", "-c default_transaction_read_only=on");
        char[] password = new EnvFileSecretResolver(envFile).resolve("PEC_DB_PASSWORD");
        try {
            properties.setProperty("password", new String(password));
            return DriverManager.getConnection(
                    "jdbc:postgresql://" + env.get(HOST_KEY) + ":" + port() + "/" + env.get("PEC_DB_NAME"), properties);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private int port() {
        return Integer.parseInt(env.get("PEC_DB_PORT"));
    }

    private String outputName(String prefix) {
        String sourceId = env.get("PEC_SOURCE_ID");
        String label = sourceId == null || sourceId.isBlank() ? "pec" : sourceId.replaceAll("[^\\w.-]", "_");
        return prefix + "-" + label + "-" + FILE_STAMP.format(Instant.now()) + ".txt";
    }

    /** {@code target/<directory>}, next to {@code target/test-classes}, wherever the build runs. */
    private static Path outputDirectory(String directory) throws URISyntaxException {
        return Path.of(LivePecInventory.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolveSibling(directory);
    }

    private static byte[] readScript(String resource) throws IOException {
        try (InputStream in = LivePecInventory.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** {@code SET TRANSACTION ... READ ONLY} first, then the read budget, before any query. */
    private static void beginReadOnlyTransaction(Connection connection) throws SQLException {
        ReadBudget budget = ReadBudget.initialEngineeringProposal();
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            statement.execute("SET statement_timeout = " + budget.statementTimeoutMs());
            statement.execute("SET lock_timeout = " + budget.lockTimeoutMs());
            statement.execute("SET idle_in_transaction_session_timeout = " + budget.idleInTransactionTimeoutMs());
        }
    }

    private static String setting(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting(?)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("no value for setting " + name);
                }
                return result.getString(1);
            }
        }
    }
}
