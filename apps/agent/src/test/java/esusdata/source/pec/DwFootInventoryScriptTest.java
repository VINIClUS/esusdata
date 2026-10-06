package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@code dw-l6-foot-inventory.sql} and its {@link DwFootInventoryGuard} over a synthetic DW on a
 * real PostgreSQL 9.6, so the script that will run against the production PEC is known to parse, to
 * pass its guard, to count the blood-pressure shapes and to find foot-exam columns. The data is
 * invented; the real column names are what the live inventory discovers.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class DwFootInventoryScriptTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_dw_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final String SCHEMA = """
            CREATE TABLE tb_fat_visita_domiciliar (co_seq_fat bigint PRIMARY KEY, nu_medicao_pressao_arterial text);
            INSERT INTO tb_fat_visita_domiciliar VALUES
                (1, '120/80'), (2, '130/90'), (3, '110x70'), (4, '140/100'), (5, '90/60'),
                (6, '12/8'), (7, 'sem medida'), (8, NULL), (9, ' 125/85 '), (10, '120 por 80');
            INSERT INTO tb_fat_visita_domiciliar SELECT 100 + g, '120/80' FROM generate_series(1, 6) g;
            CREATE TABLE tb_fat_atendimento_individual (
                co_seq_fat bigint PRIMARY KEY, st_exame_pe_diabetico boolean, ds_exame text, nu_peso numeric,
                nu_altura numeric);
            INSERT INTO tb_fat_atendimento_individual VALUES
                (1, true, 'x', 70, 170), (2, false, NULL, 80, 180), (3, NULL, NULL, NULL, NULL);
            CREATE TABLE tb_fat_cad_individual (co_seq_fat bigint PRIMARY KEY, nu_cns text);
            INSERT INTO tb_fat_cad_individual VALUES (1, '700000000000001');
            ANALYZE
            """;

    private static String output;

    @BeforeAll
    static void runTheInventory() throws Exception {
        try (Connection connection = connect()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(SCHEMA);
            }
            StringWriter text = new StringWriter();
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try (PrintWriter out = new PrintWriter(text)) {
                PsqlScriptRunner.Runner runner =
                        new PsqlScriptRunner.Runner(connection, out, DwFootInventoryGuard::check);
                for (PsqlScriptRunner.ScriptStep step : PsqlScriptRunner.parse(script())) {
                    runner.run(step);
                }
                assertThat(runner.queries()).isPositive();
            } finally {
                connection.rollback();
            }
            output = text.toString();
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    private static String script() throws IOException {
        try (InputStream in =
                DwFootInventoryScriptTest.class.getResourceAsStream(DwFootInventoryLiveTest.SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing " + DwFootInventoryLiveTest.SCRIPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void countsTheRowsTheNonNullAndTheOnesInTheBloodPressureFormat() {
        // linhas|nao_nulas|casam_formato|pct_das_nao_nulas|vazias: 16 rows, 15 non-null, 12 match.
        assertThat(output).contains("16|15|12|80.00|0");
    }

    @Test
    void listsTheCommonShapesGroupsTheRareOnesAndNeverShowsAValue() {
        // forma|linhas|formas_distintas: 120/80 x7, 130/90 and ' 125/85 ' share a shape; the other
        // shapes have one row each and are grouped.
        assertThat(output)
                .contains("999/99|9|1")
                .contains("(outras)|6|6")
                .doesNotContain("120/80|")
                .doesNotContain("sem medida");
    }

    @Test
    void findsTheFootExamColumnsAndCountsThem() {
        assertThat(output)
                .contains("tb_fat_atendimento_individual|st_exame_pe_diabetico|boolean")
                .contains("st_exame_pe_diabetico|boolean|2|3|1")
                .contains("ds_exame|text|1|3");
    }

    @Test
    void neverReadsAnotherFact() {
        assertThat(output).doesNotContain("700000000000001");
    }

    @Test
    void theGuardRefusesRowsOfAFactAndOtherFacts() throws Exception {
        try (Connection connection = connect()) {
            assertThatThrownBy(() -> DwFootInventoryGuard.check(
                            connection, "SELECT nu_medicao_pressao_arterial FROM public.tb_fat_visita_domiciliar"))
                    .hasMessageContaining("without aggregating");
            assertThatThrownBy(() ->
                            DwFootInventoryGuard.check(connection, "SELECT count(*) FROM public.tb_fat_cad_individual"))
                    .hasMessageContaining("tb_fat_cad_individual");
        }
    }
}
