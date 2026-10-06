package esusdata.source.pec;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * What the L6 and foot-exam DW inventory ({@code dw-l6-foot-inventory.sql}) may read, enforced from
 * the execution plan before each statement runs. Catalog reads always pass. A DW fact may be read
 * only if it is one of the two the inventory is about ({@link #FACTS}) and only as an aggregate,
 * looking through sort, limit, result and subquery wrappers, so no row of it ever reaches the
 * output. Nothing else is read: a table that names a person ({@link TeamInventoryGuard#DENIED_NAME}),
 * any other fact or a materialized view is refused.
 */
final class DwFootInventoryGuard {

    static final Set<String> FACTS = Set.of("tb_fat_visita_domiciliar", "tb_fat_atendimento_individual");

    private static final String NODE_TYPE = "Node Type";
    private static final String PLANS = "Plans";
    private static final Set<String> CATALOG_SCHEMAS = Set.of("pg_catalog", "information_schema");
    private static final Set<String> WRAPPERS = Set.of("Sort", "Limit", "Result", "Subquery Scan", "Unique");

    private DwFootInventoryGuard() {}

    /** The {@link PsqlScriptRunner.StatementGuard} of this inventory. */
    static void check(Connection connection, String sql) throws SQLException {
        JsonNode root = PsqlScriptRunner.plan(connection, sql);
        boolean readsFact = false;
        for (JsonNode scan : root.findParents("Relation Name")) {
            String schema = scan.path("Schema").asString();
            String relation = scan.path("Relation Name").asString();
            if (CATALOG_SCHEMAS.contains(schema)) {
                continue;
            }
            if (TeamInventoryGuard.DENIED_NAME.matcher(relation).find() || !FACTS.contains(relation)) {
                throw refuse(sql, "reads " + schema + "." + relation);
            }
            readsFact = true;
        }
        if (readsFact && !isAggregate(root.path(0).path("Plan"))) {
            throw refuse(sql, "reads rows of a DW fact without aggregating them");
        }
    }

    private static boolean isAggregate(JsonNode plan) {
        JsonNode node = plan;
        while (WRAPPERS.contains(node.path(NODE_TYPE).asString())
                && node.path(PLANS).size() == 1) {
            node = node.path(PLANS).path(0);
        }
        return "Aggregate".equals(node.path(NODE_TYPE).asString());
    }

    private static IllegalStateException refuse(String sql, String why) {
        return new IllegalStateException("refusing a statement that " + why + ": " + PsqlScriptRunner.head(sql));
    }
}
