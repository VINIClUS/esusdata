package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.config.SqliteDataSourceConfig;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.PublicationService;
import br.gov.observatorioaps.resultstore.ReproducibilityCheck;
import br.gov.observatorioaps.resultstore.ResultRepository;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import br.gov.observatorioaps.resultstore.SourceRecord;
import br.gov.observatorioaps.resultstore.SourceRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Assembles the job-runner/result-store beans as plain objects over a real {@code
 * SqliteDataSourceConfig}-migrated database — the same pattern {@code SqliteDataSourceConfigTest}
 * uses. Deliberately does not register {@code JobRunnerConfig}: that class wires a
 * {@code JobWorker} that auto-starts a background thread via {@code SmartLifecycle}, which would
 * race every test that wants to drive the state machine deterministically one step at a time.
 */
final class JobRunnerTestFixture implements AutoCloseable {

    final AnnotationConfigApplicationContext context;
    final JdbcTemplate jdbc;
    final TransactionTemplate transactionTemplate;
    final JobRepository jobRepository;
    final ResultStagingArea stagingArea;
    final ExtractionManifestRepository extractionManifestRepository;
    final SourceRepository sourceRepository;
    final ResultRepository resultRepository;
    final ReproducibilityCheck reproducibilityCheck;
    final PublicationService publicationService;
    final IndicatorRunExecutor executor;
    final CancellationRegistry cancellationRegistry = new CancellationRegistry();
    final RetryPolicy retryPolicy = new RetryPolicy(Duration.ofMillis(1), Duration.ofSeconds(1));
    final Duration liveAcquisitionCooldownMargin = Duration.ofMinutes(1);
    final Path extractsDir;
    final Clock clock;

    JobRunnerTestFixture(Path dataDir, Clock clock) {
        this.clock = clock;
        this.extractsDir = dataDir.resolve("extracts");

        context = new AnnotationConfigApplicationContext();
        context.register(SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        transactionTemplate = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        jobRepository = new JobRepository(jdbc, transactionTemplate);
        stagingArea = new ResultStagingArea(jdbc);
        extractionManifestRepository = new ExtractionManifestRepository(jdbc);
        sourceRepository = new SourceRepository(jdbc);
        resultRepository = new ResultRepository(jdbc);
        reproducibilityCheck = new ReproducibilityCheck(extractsDir);
        publicationService = new PublicationService(
                jdbc, transactionTemplate, extractionManifestRepository, reproducibilityCheck, extractsDir);
        executor = new IndicatorRunExecutor(
                extractsDir, jobRepository, stagingArea, publicationService, "test-build", clock);
    }

    JobRecovery jobRecovery() {
        return new JobRecovery(jobRepository, transactionTemplate, stagingArea,
                acquisitionGuard(), retryPolicy, clock, liveAcquisitionCooldownMargin);
    }

    AcquisitionGuard acquisitionGuard() {
        return new AcquisitionGuard(jdbc, clock);
    }

    JobWorker worker(String processInstanceId) {
        return new JobWorker(jobRepository, executor, stagingArea, cancellationRegistry,
                retryPolicy, clock, processInstanceId, Duration.ofMillis(50));
    }

    /** Registers a default source row so FK constraints on jobs/extraction_manifests/results are satisfiable. */
    String registerSource(String sourceId, String municipalityIbge) {
        sourceRepository.upsert(new SourceRecord(
                sourceId, 1, "PEC_POSTGRESQL", "PRONTUARIO", "PRIMARY",
                "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD",
                municipalityIbge, "5.4.37", "PEC_DW", Instant.EPOCH.toString()));
        return sourceId;
    }

    @Override
    public void close() {
        context.close();
    }
}
