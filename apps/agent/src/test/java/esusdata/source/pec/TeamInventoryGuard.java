package esusdata.source.pec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * What the transactional team inventory ({@code tx-team-inventory.sql}) may read, enforced from the
 * execution plan before each statement runs, not by trust in the script (ADR 0031).
 *
 * <ul>
 *   <li>Catalog-only statements (the generators and the structure pass) always pass.
 *   <li>A statement that reads a table is refused when the table is a DW fact or accompaniment
 *       ({@code tb_fat_*}, {@code tb_acomp_*}), a materialized view, a citizen or professional
 *       dimension, or a table whose name suggests a person ({@link #DENIED_NAME}); and when the
 *       planner expects to scan more than {@link #MAX_SCAN_ROWS} rows of it.
 *   <li>Such a statement must be an aggregate (counts, min/max, GROUP BY), looking through sort,
 *       limit, result and subquery wrappers, or a LIMIT over tables of at most {@link
 *       #DOMAIN_MAX_BYTES} bytes on disk (the code-domain tables whose labels the inventory needs).
 * </ul>
 *
 * <p>{@link #DENIED_NAME} repeats the {@code NEGADAS} convention of the script on purpose: the
 * script narrows what it asks for, this class is the second line.
 */
final class TeamInventoryGuard {

    static final Pattern DENIED_NAME = Pattern.compile("(?i)(cidadao|paciente|prontuario|pessoa|individuo|usuario"
            + "|senha|credencial|certificado|profissional|(^|_)prof(_|$)|(^|_)(cpf|cns)(_|$))");

    static final long MAX_SCAN_ROWS = 1_000_000;
    static final long DOMAIN_MAX_BYTES = 1_048_576;

    private static final String NODE_TYPE = "Node Type";
    private static final String PLANS = "Plans";
    private static final Set<String> CATALOG_SCHEMAS = Set.of("pg_catalog", "information_schema");
    private static final Pattern DW_DATA = Pattern.compile("^(tb_fat_|tb_acomp_|mv_)");
    private static final Set<String> WRAPPERS = Set.of("Sort", "Limit", "Result", "Subquery Scan", "Unique");

    private TeamInventoryGuard() {}

    /** The {@link PsqlScriptRunner.StatementGuard} of the transactional inventory. */
    static void check(Connection connection, String sql) throws SQLException {
        JsonNode root = PsqlScriptRunner.plan(connection, sql);
        boolean readsTables = false;
        boolean allDomainSized = true;
        for (JsonNode scan : root.findParents("Relation Name")) {
            String schema = scan.path("Schema").asString();
            String relation = scan.path("Relation Name").asString();
            if (CATALOG_SCHEMAS.contains(schema)) {
                continue;
            }
            if (DENIED_NAME.matcher(relation).find()
                    || DW_DATA.matcher(relation).find()
                    || relation.startsWith("tb_dim_cidadao")
                    || relation.startsWith("tb_dim_profissional")) {
                throw refuse(sql, "reads " + schema + "." + relation);
            }
            if (scan.path("Plan Rows").asLong() > MAX_SCAN_ROWS) {
                throw refuse(sql, "scans an estimated " + scan.path("Plan Rows").asLong() + " rows of " + relation);
            }
            readsTables = true;
            allDomainSized &= sizeOf(connection, schema, relation) <= DOMAIN_MAX_BYTES;
        }
        if (!readsTables) {
            return;
        }
        JsonNode top = root.path(0).path("Plan");
        if (isAggregate(top) || (allDomainSized && hasLimit(top))) {
            return;
        }
        throw refuse(sql, "reads rows that are neither an aggregate nor a small domain table with a LIMIT");
    }

    private static boolean isAggregate(JsonNode plan) {
        JsonNode node = plan;
        while (WRAPPERS.contains(node.path(NODE_TYPE).asString())
                && node.path(PLANS).size() == 1) {
            node = node.path(PLANS).path(0);
        }
        return "Aggregate".equals(node.path(NODE_TYPE).asString());
    }

    private static boolean hasLimit(JsonNode plan) {
        JsonNode node = plan;
        while (WRAPPERS.contains(node.path(NODE_TYPE).asString())
                && node.path(PLANS).size() == 1) {
            if ("Limit".equals(node.path(NODE_TYPE).asString())) {
                return true;
            }
            node = node.path(PLANS).path(0);
        }
        return "Limit".equals(node.path(NODE_TYPE).asString());
    }

    private static long sizeOf(Connection connection, String schema, String relation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_relation_size((quote_ident(?) || '.' || quote_ident(?))::regclass)")) {
            statement.setString(1, schema);
            statement.setString(2, relation);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("no size for " + schema + "." + relation);
                }
                return result.getLong(1);
            }
        }
    }

    private static IllegalStateException refuse(String sql, String why) {
        return new IllegalStateException("refusing a statement that " + why + ": " + PsqlScriptRunner.head(sql));
    }
}
