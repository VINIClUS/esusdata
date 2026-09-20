package br.gov.observatorioaps.api;

import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import br.gov.observatorioaps.jobrunner.AcquisitionGuard;
import br.gov.observatorioaps.jobrunner.JobRecovery;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.RetryPolicy;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
@Import(ReadinessGateTest.RecoveryProbeConfiguration.class)
class ReadinessGateTest extends SecuritySliceTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private static final String MUNICIPALITY = "3541307";
    private static final String STALE_JOB_ID = "job-" + UUID.randomUUID();
    private static final String SOURCE_ID = "src-" + UUID.randomUUID();
    private static final Duration READINESS_PROBE_WINDOW = Duration.ofSeconds(2);
    private static final CountDownLatch READINESS_PROBE_FINISHED = new CountDownLatch(1);
    private static final AtomicReference<Integer> EARLY_READY_STATUS = new AtomicReference<>();

    /**
     * Replaces the injected JobRecovery candidate with a spy that keeps the real recovery call
     * behind a controllable probe. The probe runs concurrently with bean creation, while the
     * application is still unable to serve traffic; if recovery ever becomes asynchronous, the
     * probe can observe a premature 200 from /ready before the real recovery call is released.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RecoveryProbeConfiguration {

        @Bean
        @Primary
        JobRecovery recoveryProbe(
                JobRepository jobRepository,
                TransactionTemplate sqliteTransactionTemplate,
                ResultStagingArea resultStagingArea,
                AcquisitionGuard acquisitionGuard,
                RetryPolicy retryPolicy,
                Clock clock,
                Duration liveAcquisitionCooldownMargin) {
            JobRecovery delegate = new JobRecovery(
                    jobRepository, sqliteTransactionTemplate, resultStagingArea, acquisitionGuard,
                    retryPolicy, clock, liveAcquisitionCooldownMargin);
            JobRecovery probe = Mockito.spy(delegate);
            Mockito.doAnswer(invocation -> {
                Thread readinessProbe = new Thread(
                        ReadinessGateTest::probeReadinessWhileRecoveryIsBlocked,
                        "readiness-gate-probe");
                readinessProbe.start();
                if (!READINESS_PROBE_FINISHED.await(
                        READINESS_PROBE_WINDOW.plusSeconds(1).toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("readiness probe did not finish while recovery was blocked");
                }
                return invocation.callRealMethod();
            }).when(probe).reconcile(Mockito.anyString());
            return probe;
        }
    }

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
    void aJobAbandonedByAPreviousBootIsAlreadyRecoveredByTheTimeTheApplicationIsReady()
            throws InterruptedException {
        assertThat(READINESS_PROBE_FINISHED.await(1, TimeUnit.SECONDS))
                .as("the recovery probe must run during context initialization")
                .isTrue();
        Integer earlyReadyStatus = EARLY_READY_STATUS.get();
        assertThat(earlyReadyStatus == null || earlyReadyStatus != 200)
                .as("/ready must not answer 200 while boot recovery is blocked")
                .isTrue();

        String state = jdbc.queryForObject(
                "select state from jobs where job_id = ?", String.class, STALE_JOB_ID);

        assertThat(state).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject(
                "select process_instance_id from jobs where job_id = ?", String.class, STALE_JOB_ID))
                .isNull();
    }

    private static void probeReadinessWhileRecoveryIsBlocked() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready"))
                .GET()
                .build();
        long deadline = System.nanoTime() + READINESS_PROBE_WINDOW.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                try {
                    HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString());
                    EARLY_READY_STATUS.set(response.statusCode());
                    return;
                } catch (java.io.IOException ignored) {
                    // A closed port is expected while synchronous recovery holds context refresh.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Thread.sleep(25);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            READINESS_PROBE_FINISHED.countDown();
        }
    }
}
