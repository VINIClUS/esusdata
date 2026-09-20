package br.gov.observatorioaps.api;

import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.9.4: "não iniciar outro worker enquanto o anterior ainda puder ler a fonte ou produzir
 * efeitos" — boot-time recovery must complete before the application is ready to serve.
 * {@code JobRunnerConfig}'s own comment documents this as a property of Spring's initialization
 * order ({@code jobRecoveryReport}, a plain {@code @Bean}, is created during
 * {@code finishBeanFactoryInitialization}, strictly before ANY {@code SmartLifecycle} — including
 * both {@code JobWorker} and Tomcat's own — starts in {@code finishRefresh}), not something
 * application code gates itself. This test does not re-derive that Spring guarantee; it guards
 * against a DIFFERENT, real regression: recovery becoming lazy/asynchronous inside the bean
 * method (e.g. wrapped to "speed up boot"), which would break the guarantee even though Spring's
 * own contract stays intact.
 *
 * <p>A stale {@code RUNNING} job is seeded directly against the SQLite file — with a real Flyway
 * migration and a fabricated {@code process_instance_id} from a "previous boot" — BEFORE the real
 * application context is created ({@code @DynamicPropertySource} runs ahead of context refresh,
 * same guarantee {@code SecuritySliceTestSupport}'s own dynamic properties already rely on). By
 * the time the test method runs, the full context (Tomcat included) is already up; if recovery
 * had not genuinely completed synchronously during boot, the job would still read {@code RUNNING}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessGateTest extends SecuritySliceTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private static final String MUNICIPALITY = "3541307";
    private static final String STALE_JOB_ID = "job-" + UUID.randomUUID();
    private static final String SOURCE_ID = "src-" + UUID.randomUUID();

    @DynamicPropertySource
    static void seedAStaleRunningJobBeforeTheRealContextStarts(DynamicPropertyRegistry registry) throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createDirectories(dataDir)
                .resolve("observatorio.sqlite"));

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO sources (id, source_configuration_version, source_family,
                        pec_installation_role, source_location_kind, host, port, database_name,
                        db_user, secret_ref, municipality_ibge, pec_version, read_model, created_at)
                    VALUES ('%s', 1, 'PEC_POSTGRESQL', 'PRONTUARIO', 'PRIMARY', '127.0.0.1', 5432,
                        'esus', 'esus_leitura', 'PEC_DB_PASSWORD', '%s', '5.4.37', 'PEC_DW', '%s')
                    """.formatted(SOURCE_ID, MUNICIPALITY, Instant.EPOCH));
            statement.execute("""
                    INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                        reference_period, state, attempt, max_attempts, process_instance_id,
                        execution_generation, created_at, source_id)
                    VALUES ('%s', 'run-%s', '%s', '%s', '%s', '2026-03', 'RUNNING', 1, 3,
                        'proc-from-a-previous-boot-that-crashed', 1, '%s', '%s')
                    """.formatted(STALE_JOB_ID, STALE_JOB_ID, MUNICIPALITY, C1Rule.INDICATOR_PACK,
                    C1Rule.RULE_VERSION, Instant.EPOCH, SOURCE_ID));
        }
    }

    @Test
    void aJobAbandonedByAPreviousBootIsAlreadyRecoveredByTheTimeTheApplicationIsReady() {
        // No polling, no sleep: by the time this test method runs at all, @SpringBootTest's
        // context.refresh() has already returned and Tomcat is already listening on PORT — both
        // strictly after jobRecoveryReport's bean method ran. If that ordering were ever broken,
        // this job would still read RUNNING here.
        String state = jdbc.queryForObject(
                "select state from jobs where job_id = ?", String.class, STALE_JOB_ID);

        assertThat(state).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject(
                "select process_instance_id from jobs where job_id = ?", String.class, STALE_JOB_ID))
                .isNull();
    }
}
