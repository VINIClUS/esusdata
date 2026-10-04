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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the DW metadata inventory, {@code contracts/compatibility/inventory/dw-inventory.sql}, over
 * JDBC against a real PEC and writes what it prints to {@code apps/agent/target/dw-inventory/}
 * (git-ignored, deleted by {@code mvn clean}). It is the JDBC alternative to running the same file
 * with psql; the procedure, and what may reach the public repository, is
 * {@code docs/discovery/runbook-inventario-dw.md}.
 *
 * <p>The script comes from the classpath copy of {@code contracts/compatibility} (ADR 0009), so
 * both paths run the same queries. Only the psql meta-commands the script uses are understood:
 * {@code \gexec} runs every non-null value its query returns, in order, {@code \qecho} prints its
 * text, {@code \set} and {@code \pset} shape psql's own output and are ignored; any other one fails
 * the run. The script's BEGIN, SET and ROLLBACK belong to psql: this test opens its own transaction
 * with {@code SET TRANSACTION ... READ ONLY} and the {@link ReadBudget#initialEngineeringProposal()}
 * timeouts, the same values the script sets, and always rolls back.
 *
 * <p>No patient data, by construction rather than by trust in the script: only SELECT/WITH
 * statements run, and each one is planned with {@code EXPLAIN} first. A plan that reads any
 * relation other than the catalogs, {@code tb_migracao}, {@code tb_relatorio_processamento} or a
 * code dimension ({@code tb_dim_*} except {@code tb_dim_cidadao*} and {@code tb_dim_profissional})
 * fails the run before the statement executes. Functions that run SQL text of their own, which
 * EXPLAIN cannot see into, are refused by name. The log carries counts and the output path, never a
 * value.
 *
 * <p><b>Gate:</b> the same as {@code ExecPlaneLivePecTest}: the opt-in {@code
 * -Dobservatorio.execution-plane.live-pec=true}, the secret file ({@link LivePecAssumptions#ENV_FILE}
 * unless {@code -Dobservatorio.execution-plane.live-pec.env-file} points elsewhere), a reachable
 * tunnel and a real login. The execution-plane binary is not needed. Without the opt-in the test is
 * skipped, so no ordinary build ever reads the production PEC.
 */
class DwInventoryLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DwInventoryLiveTest.class);

    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";
    private static final String SCRIPT_RESOURCE = "/compatibility/inventory/dw-inventory.sql";
    private static final String APPLICATION_NAME = "observatorio-aps-inventario";
    /** The script's {@code \pset null}, so both paths print a NULL the same way. */
    private static final String NULL_DISPLAY = "(nulo)";

    /** psql's transaction and session control: never forwarded, this test applies its own. */
    private static final Pattern PSQL_SESSION_CONTROL =
            Pattern.compile("(?i)(begin|start|commit|end|rollback|abort|set|reset)\\b");

    private static final Pattern READ_QUERY = Pattern.compile("(?i)(select|with)\\b");
    private static final Pattern SQL_RUNNING_FUNCTION = Pattern.compile("(?i)\\b(\\w+_to_xml|dblink\\w*)\\s*\\(");
    private static final Pattern META_COMMAND = Pattern.compile("\\\\(\\w++)(.*)");
    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$\\w*\\$");
    private static final Set<String> CATALOG_SCHEMAS = Set.of("pg_catalog", "information_schema");
    private static final Set<String> METADATA_TABLES = Set.of("tb_migracao", "tb_relatorio_processamento");
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path envFile;
    private Map<String, String> env;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: reads the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        String configuredEnvFile = System.getProperty(ENV_FILE_PROPERTY);
        envFile = configuredEnvFile == null || configuredEnvFile.isBlank()
                ? LivePecAssumptions.ENV_FILE
                : Path.of(configuredEnvFile);
        Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
        env = Files.readAllLines(envFile).stream()
                .filter(line -> line.contains("=") && !line.strip().startsWith("#"))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim(),
                        (first, last) -> last));
        for (String key : List.of("PEC_DB_HOST", "PEC_DB_PORT", "PEC_DB_NAME", "PEC_DB_USER")) {
            Assumptions.assumeTrue(
                    env.get(key) != null && !env.get(key).isBlank(), "Skipping: " + envFile + " has no " + key);
        }
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(env.get("PEC_DB_HOST"), port()),
                "Skipping: " + env.get("PEC_DB_HOST") + ":" + port() + " not reachable — tunnel likely down");
        // The tunnel's local port accepts TCP even when the PEC's PostgreSQL behind it is down —
        // only a real login proves there is a server to read.
        Assumptions.assumeTrue(canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
    }

    @Test
    void writesTheDwMetadataInventoryWithoutReadingPatientRows() throws Exception {
        byte[] script = readScript();
        List<ScriptStep> steps = parse(new String(script, StandardCharsets.UTF_8));
        Path output = outputDirectory().resolve(outputName());
        Files.createDirectories(output.getParent());

        try (Connection connection = open();
                PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            beginReadOnlyTransaction(connection);
            try {
                assertThat(setting(connection, "transaction_read_only")).isEqualTo("on");
                out.println("-- Inventário de metadados do DW do PEC, DwInventoryLiveTest, " + Instant.now());
                out.println("-- " + SCRIPT_RESOURCE + " sha256:" + sha256(script));
                Inventory inventory = new Inventory(connection, out);
                for (ScriptStep step : steps) {
                    inventory.run(step);
                }
                assertThat(setting(connection, "transaction_read_only")).isEqualTo("on");
                assertThat(inventory.queries()).isPositive();
                assertThat(out.checkError()).as("writing " + output).isFalse();
                log.info(
                        "dw inventory: {} queries, {} of them generated by \\gexec, written to {}",
                        inventory.queries(),
                        inventory.generatedQueries(),
                        output);
            } finally {
                connection.rollback();
            }
        }
        assertThat(output).isNotEmptyFile();
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
                    "jdbc:postgresql://" + env.get("PEC_DB_HOST") + ":" + port() + "/" + env.get("PEC_DB_NAME"),
                    properties);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private int port() {
        return Integer.parseInt(env.get("PEC_DB_PORT"));
    }

    private String outputName() {
        String sourceId = env.get("PEC_SOURCE_ID");
        String label = sourceId == null || sourceId.isBlank() ? "pec" : sourceId.replaceAll("[^\\w.-]", "_");
        return "dw-inventory-" + label + "-" + FILE_STAMP.format(Instant.now()) + ".txt";
    }

    /** {@code target/dw-inventory}, next to {@code target/test-classes}, wherever the build runs. */
    private static Path outputDirectory() throws URISyntaxException {
        return Path.of(DwInventoryLiveTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolveSibling("dw-inventory");
    }

    private static byte[] readScript() throws IOException {
        try (InputStream in = DwInventoryLiveTest.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(SCRIPT_RESOURCE + " is not on the test classpath");
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
                result.next();
                return result.getString(1);
            }
        }
    }

    private enum StepKind {
        STATEMENT,
        GEXEC,
        ECHO
    }

    private record ScriptStep(StepKind kind, String text) {}

    /**
     * Splits the psql script into statements and the meta-commands it uses. Quoted text, dollar
     * quotes and {@code --} comments are respected, as psql's own lexer does; a doubled quote is
     * read as two adjacent literals, which leaves the text unchanged.
     */
    private static List<ScriptStep> parse(String script) {
        List<ScriptStep> steps = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        int at = 0;
        while (at < script.length()) {
            at = switch (script.charAt(at)) {
                case '\'', '"' -> quoted(script, at, statement);
                case '$' -> dollarQuoted(script, at, statement);
                case '-' -> commentOrMinus(script, at, statement);
                case '\\' -> metaCommand(script, at, statement, steps);
                case ';' -> endOfStatement(at, statement, steps);
                default -> {
                    statement.append(script.charAt(at));
                    yield at + 1;
                }
            };
        }
        if (!statement.toString().isBlank()) {
            throw new IllegalStateException("the inventory script ends inside a statement: " + head(statement));
        }
        return steps;
    }

    private static int quoted(String script, int start, StringBuilder statement) {
        int close = script.indexOf(script.charAt(start), start + 1);
        if (close < 0) {
            throw new IllegalStateException("unterminated quote in the inventory script at offset " + start);
        }
        statement.append(script, start, close + 1);
        return close + 1;
    }

    private static int dollarQuoted(String script, int start, StringBuilder statement) {
        Matcher tag = DOLLAR_TAG.matcher(script).region(start, script.length());
        if (!tag.lookingAt()) {
            statement.append('$');
            return start + 1;
        }
        int close = script.indexOf(tag.group(), tag.end());
        if (close < 0) {
            throw new IllegalStateException("unterminated dollar quote in the inventory script at offset " + start);
        }
        int end = close + tag.group().length();
        statement.append(script, start, end);
        return end;
    }

    private static int commentOrMinus(String script, int start, StringBuilder statement) {
        if (script.startsWith("--", start)) {
            return lineEnd(script, start);
        }
        statement.append('-');
        return start + 1;
    }

    /** A psql meta-command runs to the end of its line; {@code \gexec} also ends a statement. */
    private static int metaCommand(String script, int start, StringBuilder statement, List<ScriptStep> steps) {
        int end = lineEnd(script, start);
        String command = script.substring(start, end).strip();
        Matcher meta = META_COMMAND.matcher(command);
        if (!meta.matches()) {
            throw new IllegalStateException("unsupported psql meta-command in the inventory script: " + command);
        }
        switch (meta.group(1)) {
            case "gexec" -> steps.add(new ScriptStep(StepKind.GEXEC, takeGenerator(statement)));
            case "qecho", "echo" -> steps.add(new ScriptStep(StepKind.ECHO, echoText(meta.group(2))));
            case "set", "pset" -> {
                // psql's own variables and output format: nothing to apply over JDBC.
            }
            default ->
                throw new IllegalStateException("unsupported psql meta-command in the inventory script: " + command);
        }
        return end;
    }

    private static int endOfStatement(int at, StringBuilder statement, List<ScriptStep> steps) {
        String text = statement.toString().strip();
        statement.setLength(0);
        if (!text.isEmpty()) {
            steps.add(new ScriptStep(StepKind.STATEMENT, text));
        }
        return at + 1;
    }

    private static String takeGenerator(StringBuilder statement) {
        String text = statement.toString().strip();
        statement.setLength(0);
        if (text.isEmpty()) {
            throw new IllegalStateException("\\gexec without a query in the inventory script");
        }
        return text;
    }

    private static String echoText(String argument) {
        String text = argument.strip();
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        return text;
    }

    private static int lineEnd(String script, int from) {
        int newline = script.indexOf('\n', from);
        return newline < 0 ? script.length() : newline;
    }

    /** The start of a statement, on one line, for a failure message: SQL text only, never a value. */
    private static String head(CharSequence sql) {
        String oneLine = sql.toString().strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "...";
    }

    private static boolean isMetadata(String schema, String relation) {
        return CATALOG_SCHEMAS.contains(schema)
                || ("public".equals(schema) && (METADATA_TABLES.contains(relation) || isCodeDimension(relation)));
    }

    private static boolean isCodeDimension(String relation) {
        return relation.startsWith("tb_dim_")
                && !relation.startsWith("tb_dim_cidadao")
                && !relation.startsWith("tb_dim_profissional");
    }

    /** psql's unaligned format: a header, one {@code |}-separated line per row, a row count. */
    private static void render(ResultSet rows, PrintWriter out) throws SQLException {
        ResultSetMetaData meta = rows.getMetaData();
        int columns = meta.getColumnCount();
        List<String> cells = new ArrayList<>(columns);
        for (int i = 1; i <= columns; i++) {
            cells.add(meta.getColumnLabel(i));
        }
        out.println(String.join("|", cells));
        int count = 0;
        while (rows.next()) {
            cells.clear();
            for (int i = 1; i <= columns; i++) {
                String value = rows.getString(i);
                cells.add(value == null ? NULL_DISPLAY : value);
            }
            out.println(String.join("|", cells));
            count++;
        }
        out.println(count == 1 ? "(1 row)" : "(" + count + " rows)");
    }

    /** Runs the parsed steps on one connection whose read-only transaction the test already opened. */
    private static final class Inventory {

        private final Connection connection;
        private final PrintWriter out;
        private int queries;
        private int generatedQueries;

        Inventory(Connection connection, PrintWriter out) {
            this.connection = connection;
            this.out = out;
        }

        int queries() {
            return queries;
        }

        int generatedQueries() {
            return generatedQueries;
        }

        void run(ScriptStep step) throws SQLException {
            switch (step.kind()) {
                case ECHO -> out.println(step.text());
                case STATEMENT -> statement(step.text());
                case GEXEC -> gexec(step.text());
            }
        }

        private void statement(String sql) throws SQLException {
            if (PSQL_SESSION_CONTROL.matcher(sql).lookingAt()) {
                return;
            }
            query(sql);
        }

        /** As psql's {@code \gexec}: every non-null value the query returns, row by row, left to right. */
        private void gexec(String generator) throws SQLException {
            requireMetadataOnly(generator);
            List<String> generated = new ArrayList<>();
            try (Statement statement = connection.createStatement()) {
                statement.setEscapeProcessing(false);
                try (ResultSet rows = statement.executeQuery(generator)) {
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        for (int i = 1; i <= columns; i++) {
                            String sql = rows.getString(i);
                            if (sql != null) {
                                generated.add(sql.strip());
                            }
                        }
                    }
                }
            }
            for (String sql : generated) {
                query(sql);
                generatedQueries++;
            }
        }

        private void query(String sql) throws SQLException {
            requireMetadataOnly(sql);
            try (Statement statement = connection.createStatement()) {
                statement.setEscapeProcessing(false);
                try (ResultSet rows = statement.executeQuery(sql)) {
                    render(rows, out);
                }
            }
            queries++;
        }

        /** Fails before execution unless the statement is a read whose plan touches only metadata. */
        private void requireMetadataOnly(String sql) throws SQLException {
            if (!READ_QUERY.matcher(sql).lookingAt()) {
                throw new IllegalStateException("refusing a statement that is not SELECT or WITH: " + head(sql));
            }
            if (SQL_RUNNING_FUNCTION.matcher(sql).find()) {
                throw new IllegalStateException("refusing a function that runs SQL of its own: " + head(sql));
            }
            try (Statement statement = connection.createStatement()) {
                statement.setEscapeProcessing(false);
                try (ResultSet plan = statement.executeQuery("EXPLAIN (VERBOSE, FORMAT JSON) " + sql)) {
                    plan.next();
                    for (JsonNode scan : MAPPER.readTree(plan.getString(1)).findParents("Relation Name")) {
                        String schema = scan.path("Schema").asString();
                        String relation = scan.path("Relation Name").asString();
                        if (!isMetadata(schema, relation)) {
                            throw new IllegalStateException(
                                    "refusing a statement that reads " + schema + "." + relation + ": " + head(sql));
                        }
                    }
                }
            }
        }
    }
}
