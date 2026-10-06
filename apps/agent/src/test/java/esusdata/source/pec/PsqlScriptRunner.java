package esusdata.source.pec;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a psql script over JDBC for the opt-in inventory tests ({@link DwInventoryLiveTest},
 * {@link TeamInventoryLiveTest}). Only the psql meta-commands the inventory scripts use are
 * understood: {@code \gexec} runs every non-null value its query returns, in order, {@code \qecho}
 * prints its text, {@code \set} and {@code \pset} shape psql's own output and are ignored; any other
 * one fails the run. The script's BEGIN, SET and ROLLBACK belong to psql and are skipped: the caller
 * opens its own read-only transaction. Every statement must be a SELECT/WITH and passes a
 * caller-supplied {@link StatementGuard} (built on {@link #plan}) before it runs.
 */
final class PsqlScriptRunner {

    /** The script's {@code \pset null}, so both paths print a NULL the same way. */
    private static final String NULL_DISPLAY = "(nulo)";

    /** psql's transaction and session control: never forwarded, the caller applies its own. */
    private static final Pattern PSQL_SESSION_CONTROL =
            Pattern.compile("(?i)(begin|start|commit|end|rollback|abort|set|reset)\\b");

    private static final Pattern READ_QUERY = Pattern.compile("(?i)(select|with)\\b");
    private static final Pattern SQL_RUNNING_FUNCTION = Pattern.compile("(?i)\\b(\\w+_to_xml|dblink\\w*)\\s*\\(");
    private static final Pattern META_COMMAND = Pattern.compile("\\\\(\\w++)(.*)");
    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$\\w*\\$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PsqlScriptRunner() {}

    enum StepKind {
        STATEMENT,
        GEXEC,
        ECHO
    }

    record ScriptStep(StepKind kind, String text) {}

    /**
     * Splits the psql script into statements and the meta-commands it uses. Quoted text, dollar
     * quotes and {@code --} comments are respected, as psql's own lexer does; a doubled quote is
     * read as two adjacent literals, which leaves the text unchanged.
     */
    static List<ScriptStep> parse(String script) {
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
    static String head(CharSequence sql) {
        String oneLine = sql.toString().strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "...";
    }

    /** psql's unaligned format: a header, one {@code |}-separated line per row, a row count. */
    static void render(ResultSet rows, PrintWriter out) throws SQLException {
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

    static final class Runner {

        private final Connection connection;
        private final PrintWriter out;
        private final StatementGuard guard;
        private int queries;
        private int generatedQueries;

        Runner(Connection connection, PrintWriter out, StatementGuard guard) {
            this.connection = connection;
            this.guard = guard;
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

        /** Fails before execution unless the statement is a read the guard accepts. */
        private void requireMetadataOnly(String sql) throws SQLException {
            if (!READ_QUERY.matcher(sql).lookingAt()) {
                throw new IllegalStateException("refusing a statement that is not SELECT or WITH: " + head(sql));
            }
            if (SQL_RUNNING_FUNCTION.matcher(sql).find()) {
                throw new IllegalStateException("refusing a function that runs SQL of its own: " + head(sql));
            }
            guard.check(connection, sql);
        }
    }

    /** The extra check a script's statements pass, after the common ones, before they run. */
    @FunctionalInterface
    interface StatementGuard {
        void check(Connection connection, String sql) throws SQLException;
    }

    /** The plan of {@code sql} (planned, not executed), as the JSON tree PostgreSQL's EXPLAIN prints. */
    static JsonNode plan(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setEscapeProcessing(false);
            try (ResultSet plan = statement.executeQuery("EXPLAIN (VERBOSE, FORMAT JSON) " + sql)) {
                if (!plan.next()) {
                    throw new SQLException("EXPLAIN returned no plan: " + head(sql));
                }
                return MAPPER.readTree(plan.getString(1));
            }
        }
    }
}
