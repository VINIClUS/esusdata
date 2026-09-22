package br.gov.observatorioaps.execution;

import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcResultStagingArea;

import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcExtractionManifestRepository;

import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcEvidenceRepository;

import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcResultRepository;

import br.gov.observatorioaps.sources.adapter.out.sqlite.JdbcSourceRepository;

import br.gov.observatorioaps.execution.adapter.out.sqlite.JdbcAcquisitionGuardStore;

import br.gov.observatorioaps.execution.adapter.out.sqlite.JdbcJobRepository;

import br.gov.observatorioaps.access.application.GrantRevalidator;
import br.gov.observatorioaps.execution.application.AcquisitionGuard;
import br.gov.observatorioaps.execution.application.CancellationRegistry;
import br.gov.observatorioaps.execution.application.IdempotencyResolver;
import br.gov.observatorioaps.execution.application.IndicatorRunExecutor;
import br.gov.observatorioaps.execution.application.JobRecovery;
import br.gov.observatorioaps.execution.domain.job.AcquisitionGuardStore;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
import br.gov.observatorioaps.execution.application.JobWorker;
import br.gov.observatorioaps.execution.domain.job.RetryPolicy;
import br.gov.observatorioaps.execution.application.SourceDiagnosticsService;
import br.gov.observatorioaps.execution.domain.extract.ExtractStore;
import br.gov.observatorioaps.execution.adapter.out.file.FileExtractStore;
import br.gov.observatorioaps.results.domain.EvidenceRepository;
import br.gov.observatorioaps.results.domain.ExtractionManifestRepository;
import br.gov.observatorioaps.results.application.PublicationService;
import br.gov.observatorioaps.results.application.ReproducibilityCheck;
import br.gov.observatorioaps.results.domain.ResultRepository;
import br.gov.observatorioaps.results.domain.ResultStagingArea;
import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionPort;
import br.gov.observatorioaps.sources.domain.SourceRepository;
import br.gov.observatorioaps.execution.domain.acquisition.AllowedDestinations;
import br.gov.observatorioaps.execution.adapter.out.pec.PecDataSourceFactory;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
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
import br.gov.observatorioaps.platform.sqlite.SqliteDataSourceConfig;
import br.gov.observatorioaps.platform.sqlite.SqliteProperties;
/**
 * Wires the job-runner + result-store beans on top of {@link SqliteDataSourceConfig}. Every bean
 * here that touches {@code jobs}/{@code results}/{@code result_staging}/{@code evidence} depends,
 * directly or transitively, on {@code flywayMigration} — migrations run before any job is
 * accepted (§1.12.2).
 *
 * <p>The PEC acquisition beans (destination allowlist, secret resolution, the PEC pool factory,
 * and the {@link AcquisitionPort} implementation) are wired in {@link
 * br.gov.observatorioaps.execution.AcquisitionConfig} — this class only consumes them by type
 * (ADR 0012: {@code execution.application} reaches the PEC only through the port, never a
 * concrete adapter).
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

    // --- results ---------------------------------------------------------------------------

    @Bean
    @DependsOn("flywayMigration")
    public SourceRepository sourceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcSourceRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ExtractionManifestRepository extractionManifestRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcExtractionManifestRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultStagingArea resultStagingArea(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcResultStagingArea(sqliteJdbcTemplate);
    }

    @Bean
    public ReproducibilityCheck reproducibilityCheck(SqliteProperties properties) {
        return new ReproducibilityCheck(properties.extractsDirectory());
    }

    @Bean
    public ExtractStore extractStore(SqliteProperties properties) {
        return new FileExtractStore(properties.extractsDirectory());
    }

    @Bean
    @DependsOn("flywayMigration")
    public PublicationService publicationService(
            JdbcTemplate sqliteJdbcTemplate,
            TransactionTemplate sqliteTransactionTemplate,
            JobRepository jobRepository,
            ExtractionManifestRepository extractionManifestRepository,
            ReproducibilityCheck reproducibilityCheck,
            SqliteProperties properties,
            GrantRevalidator grantRevalidator) {
        return new PublicationService(sqliteJdbcTemplate, sqliteTransactionTemplate,
                jobRepository,
                extractionManifestRepository, reproducibilityCheck, properties.extractsDirectory(),
                grantRevalidator);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultRepository resultRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcResultRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public EvidenceRepository evidenceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcEvidenceRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public SourceDiagnosticsService sourceDiagnosticsService(
            SourceRepository sourceRepository, AllowedDestinations allowedDestinations,
            PecDataSourceFactory pecDataSourceFactory) {
        return new SourceDiagnosticsService(sourceRepository, allowedDestinations, pecDataSourceFactory);
    }

    // --- job runner ------------------------------------------------------------------------

    @Bean
    @DependsOn("flywayMigration")
    public JobRepository jobRepository(JdbcTemplate sqliteJdbcTemplate, TransactionTemplate sqliteTransactionTemplate) {
        return new JdbcJobRepository(sqliteJdbcTemplate, sqliteTransactionTemplate);
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
    public AcquisitionGuardStore acquisitionGuardStore(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcAcquisitionGuardStore(sqliteJdbcTemplate);
    }

    @Bean
    public AcquisitionGuard acquisitionGuard(AcquisitionGuardStore acquisitionGuardStore, Clock clock) {
        return new AcquisitionGuard(acquisitionGuardStore, clock);
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
     * JobWorker#start()} (or any HTTP request) can ever run. {@code finishBeanFactoryInitialization}
     * (where every plain {@code @Bean} factory method, including this one, executes) always
     * completes before {@code finishRefresh} starts {@code SmartLifecycle} beans — and Boot's
     * embedded Tomcat only starts ACCEPTING connections from its own {@code SmartLifecycle}
     * ({@code WebServerStartStopLifecycle}), not from context creation. So recovery finishing
     * before the first HTTP byte is ever processed is a property of Spring's own initialization
     * order, not a mechanism this class has to build — {@code ReadinessGateTest} documents and
     * protects that ordering rather than gating anything itself.
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
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea resultStagingArea,
            PublicationService publicationService,
            @Value("${observatorio.app.build:dev}") String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            AcquisitionPort acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin) {
        return new IndicatorRunExecutor(extractStore, jobRepository,
                resultStagingArea, publicationService, appBuild, clock, grantRevalidator,
                sourceRepository, acquisitionPort, acquisitionGuard, liveAcquisitionCooldownMargin);
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
