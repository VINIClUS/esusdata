package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Runs the DW metadata inventory, {@code contracts/compatibility/inventory/dw-inventory.sql}, over
 * JDBC against a real PEC and writes what it prints to {@code apps/agent/target/dw-inventory/}
 * (git-ignored, deleted by {@code mvn clean}). It is the JDBC alternative to running the same file
 * with psql; the procedure, and what may reach the public repository, is
 * {@code docs/discovery/runbook-inventario-dw.md}.
 *
 * <p>The session, the gate and the script runner are shared with {@link TeamInventoryLiveTest}
 * ({@link LivePecInventory}, {@link PsqlScriptRunner}).
 *
 * <p>No patient data, by construction rather than by trust in the script: only SELECT/WITH
 * statements run, and each one is planned with {@code EXPLAIN} first. A plan that reads any
 * relation other than the catalogs, {@code tb_migracao}, {@code tb_relatorio_processamento} or a
 * code dimension ({@code tb_dim_*} except {@code tb_dim_cidadao*} and {@code tb_dim_profissional})
 * fails the run before the statement executes. Functions that run SQL text of their own, which
 * EXPLAIN cannot see into, are refused by name.
 */
class DwInventoryLiveTest {

    private static final String SCRIPT_RESOURCE = "/compatibility/inventory/dw-inventory.sql";
    private static final Set<String> CATALOG_SCHEMAS = Set.of("pg_catalog", "information_schema");
    private static final Set<String> METADATA_TABLES = Set.of("tb_migracao", "tb_relatorio_processamento");

    @Test
    void writesTheDwMetadataInventoryWithoutReadingPatientRows() throws Exception {
        Path output = LivePecInventory.assumeAvailable()
                .run(
                        SCRIPT_RESOURCE,
                        "dw-inventory",
                        "dw-inventory",
                        "Inventário de metadados do DW do PEC, DwInventoryLiveTest",
                        DwInventoryLiveTest::requireMetadataOnly);

        assertThat(output).isNotEmptyFile();
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

    /** Fails before execution unless the statement's plan touches only metadata. */
    static void requireMetadataOnly(Connection connection, String sql) throws SQLException {
        for (JsonNode scan : PsqlScriptRunner.plan(connection, sql).findParents("Relation Name")) {
            String schema = scan.path("Schema").asString();
            String relation = scan.path("Relation Name").asString();
            if (!isMetadata(schema, relation)) {
                throw new IllegalStateException("refusing a statement that reads " + schema + "." + relation + ": "
                        + PsqlScriptRunner.head(sql));
            }
        }
    }
}
