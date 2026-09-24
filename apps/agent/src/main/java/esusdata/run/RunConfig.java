package esusdata.run;

import esusdata.source.SourceConnectionProperties;
import esusdata.run.acquisition.ExecPlaneAcquisition;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.EnvFileSecretResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import esusdata.result.JdbcResultStagingArea;
import esusdata.result.JdbcExtractionManifestRepository;
import esusdata.result.JdbcEvidenceRepository;
import esusdata.result.JdbcResultRepository;
import esusdata.source.JdbcSourceRepository;
import esusdata.run.job.JdbcAcquisitionGuardStore;
import esusdata.run.job.JdbcJobRepository;
import esusdata.auth.GrantRevalidator;
import esusdata.run.worker.AcquisitionGuard;
import esusdata.run.worker.CancellationRegistry;
import esusdata.run.worker.IdempotencyResolver;
import esusdata.run.worker.RunExecutor;
import esusdata.run.worker.JobRecovery;
import esusdata.run.job.AcquisitionGuardStore;
import esusdata.run.job.JobRepository;
import esusdata.run.worker.JobWorker;
import esusdata.run.job.RetryPolicy;
import esusdata.source.SourceDiagnosticsService;
import esusdata.run.extract.ExtractStore;
import esusdata.run.extract.FileExtractStore;
import esusdata.result.model.EvidenceRepository;
import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.PublicationService;
import esusdata.result.ReproducibilityCheck;
import esusdata.result.model.ResultRepository;
import esusdata.result.model.ResultStagingArea;
import esusdata.run.acquisition.Acquisition;
import esusdata.source.SourceRepository;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.ReadBudget;
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
import esusdata.config.SqliteConfig;
import esusdata.config.SqliteProperties;
/**
 * Wires the job-runner + result-store beans on top of {@link SqliteConfig}. Every bean
 * here that touches {@code jobs}/{@code results}/{@code result_staging}/{@code evidence} depends,
 * directly or transitively, on {@code flywayMigration} — migrations run before any job is
 * accepted (§1.12.2).
 */
@Configuration
@EnableConfigurationProperties({SourceConnectionProperties.class, ExecPlaneProperties.class})
public class RunConfig {

    private static final Logger log = LoggerFactory.getLogger(RunConfig.class);

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

    // --- jobrunner -------------------------------------------------------------------------

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
    public RunExecutor indicatorRunExecutor(
            ExtractStore extractStore,
            JobRepository jobRepository,
            ResultStagingArea resultStagingArea,
            PublicationService publicationService,
            @Value("${observatorio.app.build:dev}") String appBuild,
            Clock clock,
            GrantRevalidator grantRevalidator,
            SourceRepository sourceRepository,
            Acquisition acquisitionPort,
            AcquisitionGuard acquisitionGuard,
            Duration liveAcquisitionCooldownMargin) {
        return new RunExecutor(extractStore, jobRepository,
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
            RunExecutor indicatorRunExecutor,
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

    /**
     * §1.12.6/ENG-46: deployment-administered, never widened by a runtime call. Empty by default
     * — a fresh install authorizes no destination until an operator configures
     * {@code observatorio.source.allowed-destinations} (a list of {@code "host:port"} entries).
     */
    @Bean
    public AllowedDestinations allowedDestinations(SourceConnectionProperties properties) {
        Set<AllowedDestinations.HostPort> parsed = new HashSet<>();
        for (String entry : orEmpty(properties.allowedDestinations())) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                throw new IllegalArgumentException(
                        "observatorio.source.allowed-destinations entry must be host:port, got: " + entry);
            }
            String host = entry.substring(0, colon);
            int port = Integer.parseInt(entry.substring(colon + 1));
            parsed.add(new AllowedDestinations.HostPort(host, port));
        }
        return new AllowedDestinations(parsed);
    }

    /**
     * Dev-only credential resolver (ADR-0002/0003) — the same env-file convention already used by
     * the live PEC tests. Production secret storage remains a documented pending item (§1.12.7).
     */
    @Bean
    public PecSecretResolver pecSecretResolver(SourceConnectionProperties properties) {
        String configured = properties.secretFile();
        Path secretFile = (configured == null || configured.isBlank())
                ? Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env")
                : Path.of(configured);
        return new EnvFileSecretResolver(secretFile);
    }

    @Bean
    public PecDataSourceFactory pecDataSourceFactory(
            AllowedDestinations allowedDestinations, PecSecretResolver pecSecretResolver) {
        return new PecDataSourceFactory(allowedDestinations, pecSecretResolver);
    }

    /**
     * ADR 0016: the Rust execution plane is the only production acquisition path — there is no
     * in-process JDBC fallback. A missing or non-executable {@code observatorio.execution-plane.binary}
     * fails startup here rather than failing every run later.
     */
    @Bean
    public Acquisition acquisitionPort(
            SqliteProperties properties, Clock clock,
            ExecPlaneProperties executionPlaneProperties, PecSecretResolver pecSecretResolver,
            AllowedDestinations allowedDestinations) {
        String binary = executionPlaneProperties.binary();
        if (binary == null || binary.isBlank() || !Files.isExecutable(Path.of(binary))) {
            throw new IllegalStateException(
                    "observatorio.execution-plane.binary must point to the observatorio-execplane executable "
                            + "(ADR 0016: the execution plane is the only acquisition path); got '"
                            + (binary == null ? "" : binary) + "'");
        }
        return new ExecPlaneAcquisition(
                List.of(binary), pecSecretResolver, allowedDestinations, properties.extractsDirectory(), clock,
                executionPlaneProperties.exitGrace());
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
