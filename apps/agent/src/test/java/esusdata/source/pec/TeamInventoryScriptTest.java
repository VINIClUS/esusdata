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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@code tx-team-inventory.sql} and its {@link TeamInventoryGuard} over a synthetic transactional
 * schema on a real PostgreSQL 9.6 (the PEC's major version), so the script that will run against the
 * production PEC is known to parse, to pass its own guard and to find a team type, its INE, its
 * municipality and its history. The table and column names are invented stand-ins: the real ones are
 * what the live inventory discovers (ADR 0031).
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class TeamInventoryScriptTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6")
            .withDatabaseName("esus_tx_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final String SCHEMA = """
            CREATE TABLE tb_dim_equipe (co_seq_dim_equipe bigint PRIMARY KEY, nu_ine text, no_equipe text);
            INSERT INTO tb_dim_equipe VALUES (1, '0000000001', 'A'), (2, '0000000002', 'B');
            CREATE TABLE tb_dim_municipio (co_seq_dim_municipio bigint PRIMARY KEY, co_ibge text);
            CREATE TABLE tb_fat_atendimento_individual (co_seq_fat bigint PRIMARY KEY, co_dim_equipe bigint);
            CREATE TABLE tb_cidadao_equipe (co_cidadao bigint PRIMARY KEY, no_cidadao text, co_tipo_equipe int);
            INSERT INTO tb_cidadao_equipe VALUES (1, 'Pessoa Ficticia', 70);
            CREATE TABLE tb_municipio (co_municipio int PRIMARY KEY, co_ibge text, no_municipio text);
            INSERT INTO tb_municipio VALUES (1, '1100015', 'M1'), (2, '3550308', 'M2');
            CREATE TABLE tb_unidade_saude (
                co_unidade int PRIMARY KEY, nu_cnes text, co_municipio int REFERENCES tb_municipio);
            INSERT INTO tb_unidade_saude VALUES (10, '1111111', 1), (20, '2222222', 2);
            CREATE TABLE tb_tipo_equipe (co_tipo_equipe int PRIMARY KEY, no_tipo_equipe text);
            INSERT INTO tb_tipo_equipe VALUES (70, 'eSF'), (76, 'eAP');
            CREATE TABLE tb_equipe (
                co_seq_equipe int PRIMARY KEY, nu_ine text,
                co_tipo_equipe int REFERENCES tb_tipo_equipe, co_unidade int REFERENCES tb_unidade_saude,
                st_ativo int, dt_inicio date);
            INSERT INTO tb_equipe VALUES
                (1, '0000000001', 70, 10, 1, '2024-01-01'),
                (2, '0000000002', 76, 10, 1, '2024-06-01'),
                (3, '0000000003', 70, 20, 0, '2023-01-01');
            CREATE TABLE tb_equipe_historico (nu_ine text, co_tipo_equipe int, dt_inicio date, dt_fim date);
            INSERT INTO tb_equipe_historico VALUES
                ('0000000001', 70, '2020-01-01', '2023-12-31'),
                ('0000000001', 76, '2024-01-01', NULL),
                ('0000000002', 76, '2024-06-01', NULL);
            ANALYZE
            """;

    /** A role like {@code esus_leitura}: SELECT on every table but one candidate, nothing else. */
    private static final String READER = "tx_reader";

    private static String output;

    @BeforeAll
    static void runTheInventory() throws Exception {
        try (Connection connection = superuser()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(SCHEMA);
                statement.execute("CREATE ROLE " + READER + " LOGIN PASSWORD 'reader'");
                statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + READER);
                statement.execute("REVOKE SELECT ON tb_equipe_historico FROM " + READER);
            }
            output = run(connection, script(TeamInventoryLiveTest.SCRIPT_RESOURCE), TeamInventoryGuard::check);
        }
    }

    private static Connection superuser() throws SQLException {
        return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    }

    /** One read-only transaction, rolled back, as the live tests run a script. */
    private static String run(Connection connection, String script, PsqlScriptRunner.StatementGuard guard)
            throws SQLException {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        StringWriter text = new StringWriter();
        try (PrintWriter out = new PrintWriter(text)) {
            PsqlScriptRunner.Runner runner = new PsqlScriptRunner.Runner(connection, out, guard);
            for (PsqlScriptRunner.ScriptStep step : PsqlScriptRunner.parse(script)) {
                runner.run(step);
            }
            assertThat(runner.queries()).isPositive();
        } finally {
            connection.rollback();
        }
        return text.toString();
    }

    @Test
    void findsTheTypeColumnItsCodesAndTheirLabels() {
        assertThat(output)
                .contains("public.tb_equipe.co_tipo_equipe|70|2")
                .contains("public.tb_equipe.co_tipo_equipe|76|1")
                .contains("public.tb_tipo_equipe|70|eSF")
                .contains("public.tb_tipo_equipe|76|eAP");
    }

    @Test
    void relatesTheTransactionalIneToTheDwIne() {
        // coluna_ine|linhas|ines_distintos|ines_tambem_no_dw: three INEs, two of them in tb_dim_equipe.
        assertThat(output).contains("public.tb_equipe.nu_ine|3|3|2").contains("equipes_dw|ines_distintos_dw");
    }

    @Test
    void findsTheMunicipalityPathAndTheTypeHistory() {
        assertThat(output)
                .contains("public.tb_equipe.co_unidade -> public.tb_unidade_saude.co_municipio -> public.tb_municipio")
                .contains("|1100015|2")
                .contains("|3550308|1")
                // ines|ines_com_mais_de_uma_linha|ines_com_mais_de_um_tipo|max_linhas_por_ine
                .contains("public.tb_equipe_historico (nu_ine, co_tipo_equipe)|2|1|1|2")
                .contains("public.tb_equipe (nu_ine, co_tipo_equipe)|3|0|0|1");
    }

    @Test
    void neverReadsAPersonTable() {
        assertThat(output).doesNotContain("Pessoa Ficticia").doesNotContain("tb_cidadao_equipe.");
        assertThat(output).contains("tb_cidadao_equipe|").contains("nome negado");
    }

    @Test
    void aRoleWithoutSelectOnACandidateGetsAFindingNotACrash() throws Exception {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), READER, "reader")) {
            String asReader = run(connection, script(TeamInventoryLiveTest.SCRIPT_RESOURCE), TeamInventoryGuard::check);

            assertThat(asReader)
                    .contains("public.tb_equipe.co_tipo_equipe|70|2")
                    .contains("sem SELECT para este papel")
                    // 1.3: pode_ler is false for the table the role cannot read, and it was not read.
                    .contains("tb_equipe_historico|tabela|f|equipe,hist|f|")
                    .doesNotContain("public.tb_equipe_historico (nu_ine, co_tipo_equipe)");
        }
    }

    @Test
    void theDwInventoryStillRunsThroughTheSharedRunner() throws Exception {
        try (Connection connection = superuser()) {
            String dw = run(
                    connection,
                    script("/compatibility/inventory/dw-inventory.sql"),
                    DwInventoryLiveTest::requireMetadataOnly);

            assertThat(dw).contains("=== FIM DO INVENTÁRIO").contains("tb_dim_equipe");
        }
    }

    @Test
    void theGuardRefusesWhatIsNotAnAggregateOrASmallDomainTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            for (String sql : List.of(
                    "SELECT nu_ine FROM tb_equipe",
                    "SELECT count(*) FROM tb_cidadao_equipe",
                    "SELECT count(*) FROM tb_fat_atendimento_individual",
                    "SELECT * FROM tb_dim_equipe")) {
                assertThatThrownBy(() -> TeamInventoryGuard.check(connection, sql))
                        .as(sql)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("refusing a statement");
            }
            TeamInventoryGuard.check(connection, "SELECT co_tipo_equipe, count(*) FROM tb_equipe GROUP BY 1");
            TeamInventoryGuard.check(connection, "SELECT relname FROM pg_class");
        }
    }

    private static String script(String resource) throws IOException {
        try (InputStream in = TeamInventoryScriptTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
