package br.gov.observatorioaps.config;

import br.gov.observatorioaps.jobrunner.AcquisitionGuard;
import br.gov.observatorioaps.jobrunner.CancellationRegistry;
import br.gov.observatorioaps.jobrunner.IdempotencyResolver;
import br.gov.observatorioaps.jobrunner.IndicatorRunExecutor;
import br.gov.observatorioaps.jobrunner.JobRecovery;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobWorker;
import br.gov.observatorioaps.jobrunner.RetryPolicy;
import br.gov.observatorioaps.resultstore.EvidenceRepository;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.PublicationService;
import br.gov.observatorioaps.resultstore.ReproducibilityCheck;
import br.gov.observatorioaps.resultstore.ResultRepository;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import br.gov.observatorioaps.resultstore.SourceRepository;
import br.gov.observatorioaps.sourceconnector.ReadBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Wires the job-runner + result-store beans on top of {@link SqliteDataSourceConfig}. Every bean
 * here that touches {@code jobs}/{@code results}/{@code result_staging}/{@code evidence} depends,
 * directly or transitively, on {@code flywayMigration} — migrations run before any job is
 * accepted (§1.12.2).
 */
@Configuration
public class JobRunnerConfig {

    private static final Logger log = LoggerFactory.getLogger(JobRunnerConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** New per process start — §1.9.4: "identidade de processo é nova a cada inicialização." */
    @Bean
    public String processInstanceId() {
        return UUID.randomUUID().toString();
    }

    @Bean
    public TransactionTemplate sqliteTransactionTemplate(DataSourceTransactionManager sqliteTransactionManager) {
        return new TransactionTemplate(sqliteTransactionManager);
    }

    // --- resultstore -----------------------------------------------------------------------

    @Bean
    @DependsOn("flywayMigration")
    public SourceRepository sourceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new SourceRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ExtractionManifestRepository extractionManifestRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new ExtractionManifestRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultStagingArea resultStagingArea(JdbcTemplate sqliteJdbcTemplate) {
        return new ResultStagingArea(sqliteJdbcTemplate);
    }

    @Bean
    public ReproducibilityCheck reproducibilityCheck(SqliteProperties properties) {
        return new ReproducibilityCheck(properties.extractsDirectory());
    }

    @Bean
    @DependsOn("flywayMigration")
    public PublicationService publicationService(
            JdbcTemplate sqliteJdbcTemplate,
            TransactionTemplate sqliteTransactionTemplate,
            ExtractionManifestRepository extractionManifestRepository,
            ReproducibilityCheck reproducibilityCheck,
            SqliteProperties properties) {
        return new PublicationService(sqliteJdbcTemplate, sqliteTransactionTemplate,
                extractionManifestRepository, reproducibilityCheck, properties.extractsDirectory());
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultRepository resultRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new ResultRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public EvidenceRepository evidenceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new EvidenceRepository(sqliteJdbcTemplate);
    }

    // --- jobrunner -------------------------------------------------------------------------

    @Bean
    @DependsOn("flywayMigration")
    public JobRepository jobRepository(JdbcTemplate sqliteJdbcTemplate, TransactionTemplate sqliteTransactionTemplate) {
        return new JobRepository(sqliteJdbcTemplate, sqliteTransactionTemplate);
    }

    @Bean
    public IdempotencyResolver idempotencyResolver(JobRepository jobRepository, Clock clock) {
        return new IdempotencyResolver(jobRepository, clock);
    }

    @Bean
    public CancellationRegistry cancellationRegistry() {
        return new CancellationRegistry();
    }

    @Bean
    public RetryPolicy retryPolicy() {
        return RetryPolicy.defaultPolicy();
    }

    @Bean
    @DependsOn("flywayMigration")
    public AcquisitionGuard acquisitionGuard(JdbcTemplate sqliteJdbcTemplate, Clock clock) {
        return new AcquisitionGuard(sqliteJdbcTemplate, clock);
    }

    /**
     * Margin for the ENG-51 cooldown: the effective {@code ReadBudget}'s statement and
     * idle-in-transaction timeouts are what actually bound how long an abandoned PostgreSQL
     * session can remain able to act — not a guessed constant — plus a fixed safety margin for
     * clock skew and connection teardown.
     */
    @Bean
    public Duration liveAcquisitionCooldownMargin() {
        ReadBudget budget = ReadBudget.initialEngineeringProposal();
        return Duration.ofMillis(budget.statementTimeoutMs() + budget.idleInTransactionTimeoutMs())
                .plus(Duration.ofSeconds(5));
    }

    @Bean
    public JobRecovery jobRecovery(
            JobRepository jobRepository,
            TransactionTemplate sqliteTransactionTemplate,
            ResultStagingArea resultStagingArea,
            AcquisitionGuard acquisitionGuard,
            RetryPolicy retryPolicy,
            Clock clock,
            Duration liveAcquisitionCooldownMargin) {
        return new JobRecovery(jobRepository, sqliteTransactionTemplate, resultStagingArea,
                acquisitionGuard, retryPolicy, clock, liveAcquisitionCooldownMargin);
    }

    /**
     * Runs boot-time recovery eagerly, as a side effect of bean creation — before {@link
     * JobWorker#start()} can ever run (Spring starts {@code SmartLifecycle} beans only after the
     * context has finished refreshing, i.e. after every {@code @Bean} factory method here has
     * already returned).
     */
    @Bean
    @DependsOn({"flywayMigration", "jobRepository"})
    public JobRecovery.RecoveryReport jobRecoveryReport(JobRecovery jobRecovery, String processInstanceId) {
        JobRecovery.RecoveryReport report = jobRecovery.reconcile(processInstanceId);
        log.info("Job recovery on boot: {} requeued, {} failed, {} cancelled",
                report.requeued(), report.failed(), report.cancelled());
        return report;
    }

    @Bean
    public IndicatorRunExecutor indicatorRunExecutor(
            SqliteProperties properties,
            JobRepository jobRepository,
            ResultStagingArea resultStagingArea,
            PublicationService publicationService,
            @Value("${observatorio.app.build:dev}") String appBuild,
            Clock clock) {
        return new IndicatorRunExecutor(properties.extractsDirectory(), jobRepository,
                resultStagingArea, publicationService, appBuild, clock);
    }

    // JobWorker implements SmartLifecycle — Spring's lifecycle processor calls start()/stop()
    // automatically once the context has finished refreshing, i.e. strictly after every @Bean
    // factory method (including jobRecoveryReport) has already run. No initMethod needed here.
    @Bean
    @DependsOn("jobRecoveryReport")
    public JobWorker jobWorker(
            JobRepository jobRepository,
            IndicatorRunExecutor indicatorRunExecutor,
            ResultStagingArea resultStagingArea,
            CancellationRegistry cancellationRegistry,
            RetryPolicy retryPolicy,
            Clock clock,
            String processInstanceId,
            @Value("${observatorio.job-runner.poll-interval-ms:2000}") long pollIntervalMs) {
        return new JobWorker(jobRepository, indicatorRunExecutor, resultStagingArea,
                cancellationRegistry, retryPolicy, clock, processInstanceId,
                Duration.ofMillis(pollIntervalMs));
    }
}
