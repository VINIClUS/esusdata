package br.gov.observatorioaps.jobrunner.application;

import br.gov.observatorioaps.resultstore.infrastructure.jdbc.JdbcResultStagingArea;

import br.gov.observatorioaps.resultstore.infrastructure.jdbc.JdbcExtractionManifestRepository;

import br.gov.observatorioaps.resultstore.infrastructure.jdbc.JdbcResultRepository;

import br.gov.observatorioaps.sourceconnector.infrastructure.jdbc.JdbcSourceRepository;

import br.gov.observatorioaps.identityaccess.infrastructure.jdbc.JdbcGrantRepository;

import br.gov.observatorioaps.identityaccess.infrastructure.jdbc.JdbcUserRepository;

import br.gov.observatorioaps.jobrunner.infrastructure.jdbc.JdbcJobRepository;

import br.gov.observatorioaps.platform.sqlite.SqliteDataSourceConfig;
import br.gov.observatorioaps.identityaccess.domain.Grant;
import br.gov.observatorioaps.identityaccess.domain.GrantRepository;
import br.gov.observatorioaps.identityaccess.application.GrantRevalidator;
import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.identityaccess.domain.ScopeKind;
import br.gov.observatorioaps.identityaccess.application.ScopeResolver;
import br.gov.observatorioaps.identityaccess.domain.UserAccount;
import br.gov.observatorioaps.identityaccess.domain.UserRepository;
import br.gov.observatorioaps.identityaccess.domain.UserState;
import br.gov.observatorioaps.resultstore.domain.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.application.PublicationService;
import br.gov.observatorioaps.resultstore.application.ReproducibilityCheck;
import br.gov.observatorioaps.resultstore.domain.ResultRepository;
import br.gov.observatorioaps.resultstore.domain.ResultStagingArea;
import br.gov.observatorioaps.sourceconnector.domain.SourceRecord;
import br.gov.observatorioaps.sourceconnector.domain.SourceRepository;
import br.gov.observatorioaps.sourceconnector.domain.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.infrastructure.file.EnvFileSecretResolver;
import br.gov.observatorioaps.sourceconnector.infrastructure.jdbc.PecDataSourceFactory;
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
import java.util.Set;
import java.util.UUID;
import br.gov.observatorioaps.jobrunner.domain.EnqueueRequest;
import br.gov.observatorioaps.jobrunner.domain.RetryPolicy;
import br.gov.observatorioaps.jobrunner.domain.JobRepository;
/**
 * Assembles the job-runner/result-store/identity-access beans as plain objects over a real {@code
 * SqliteDataSourceConfig}-migrated database — the same pattern {@code SqliteDataSourceConfigTest}
 * uses. Deliberately does not register {@code JobRunnerConfig}: that class wires a
 * {@code JobWorker} that auto-starts a background thread via {@code SmartLifecycle}, which would
 * race every test that wants to drive the state machine deterministically one step at a time.
 *
 * <p>Uses a real {@link GrantRevalidator} (backed by the same migrated database), not a
 * permissive stub — §1.9.4 L365 revalidation is a production invariant this fixture must exercise
 * honestly. Tests that need an authorized run must call {@link #registerPrincipal} and use its
 * returned id as {@code EnqueueRequest.idempotencyPrincipal}.
 */
public final class JobRunnerTestFixture implements AutoCloseable {

    public final AnnotationConfigApplicationContext context;
    public final JdbcTemplate jdbc;
    public final TransactionTemplate transactionTemplate;
    public final JobRepository jobRepository;
    public final ResultStagingArea stagingArea;
    public final ExtractionManifestRepository extractionManifestRepository;
    public final SourceRepository sourceRepository;
    public final ResultRepository resultRepository;
    public final ReproducibilityCheck reproducibilityCheck;
    public final PublicationService publicationService;
    public final IndicatorRunExecutor executor;
    public final UserRepository userRepository;
    public final GrantRepository grantRepository;
    public final GrantRevalidator grantRevalidator;
    public final CancellationRegistry cancellationRegistry = new CancellationRegistry();
    public final RetryPolicy retryPolicy = new RetryPolicy(Duration.ofMillis(1), Duration.ofSeconds(1));
    public final Duration liveAcquisitionCooldownMargin = Duration.ofMinutes(1);
    public final Path extractsDir;
    public final Clock clock;

    public JobRunnerTestFixture(Path dataDir, Clock clock) {
        this(dataDir, clock, null);
    }

    /**
     * @param pecDataSourceFactory when non-null, used instead of the default no-destination-
     *     allowed factory — lets a live-acquisition test point {@code runLive} at a real
     *     Testcontainers PostgreSQL instance.
     */
    public JobRunnerTestFixture(Path dataDir, Clock clock, PecDataSourceFactory pecDataSourceFactory) {
        this.clock = clock;
        this.extractsDir = dataDir.resolve("extracts");

        context = new AnnotationConfigApplicationContext();
        context.register(SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        transactionTemplate = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        jobRepository = new JdbcJobRepository(jdbc, transactionTemplate);
        stagingArea = new JdbcResultStagingArea(jdbc);
        extractionManifestRepository = new JdbcExtractionManifestRepository(jdbc);
        sourceRepository = new JdbcSourceRepository(jdbc);
        resultRepository = new JdbcResultRepository(jdbc);
        reproducibilityCheck = new ReproducibilityCheck(extractsDir);

        userRepository = new JdbcUserRepository(jdbc);
        grantRepository = new JdbcGrantRepository(jdbc);
        ScopeResolver scopeResolver = new ScopeResolver(jdbc);
        grantRevalidator = new GrantRevalidator(scopeResolver, userRepository);

        publicationService = new PublicationService(
                jdbc, transactionTemplate, jobRepository, extractionManifestRepository, reproducibilityCheck,
                extractsDir, grantRevalidator);
        // No destination is ever allow-listed by default — extract-only tests never call
        // PecDataSourceFactory.open, and EnvFileSecretResolver only touches this path lazily,
        // inside resolve(), which live-acquisition tests exercise with their own real secret file.
        PecDataSourceFactory factory = pecDataSourceFactory != null ? pecDataSourceFactory
                : new PecDataSourceFactory(
                        new AllowedDestinations(Set.of()), new EnvFileSecretResolver(dataDir.resolve("unused.env")));
        executor = new IndicatorRunExecutor(
                extractsDir, jobRepository, stagingArea, publicationService, "test-build", clock,
                grantRevalidator, sourceRepository, factory, acquisitionGuard(), liveAcquisitionCooldownMargin);
    }

    public JobRecovery jobRecovery() {
        return new JobRecovery(jobRepository, transactionTemplate, stagingArea,
                acquisitionGuard(), retryPolicy, clock, liveAcquisitionCooldownMargin);
    }

    public AcquisitionGuard acquisitionGuard() {
        return new AcquisitionGuard(jdbc, clock);
    }

    public JobWorker worker(String processInstanceId) {
        return new JobWorker(jobRepository, executor, stagingArea, cancellationRegistry,
                retryPolicy, clock, processInstanceId, Duration.ofMillis(50));
    }

    /** Registers a default source row so FK constraints on jobs/extraction_manifests/results are satisfiable. */
    public String registerSource(String sourceId, String municipalityIbge) {
        sourceRepository.upsert(new SourceRecord(
                sourceId, 1, "PEC_POSTGRESQL", "PRONTUARIO", "PRIMARY",
                "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD",
                municipalityIbge, "5.4.37", "PEC_DW", Instant.EPOCH.toString()));
        return sourceId;
    }

    /**
     * Registers an ACTIVE user with a MANAGER grant scoped to {@code municipalityIbge} — MANAGER
     * is the only seeded role carrying {@code run_indicator} (see V3's {@code role_permissions}).
     * Returns the user id, to be used as {@code EnqueueRequest.idempotencyPrincipal}.
     */
    public String registerPrincipal(String userId, String municipalityIbge) {
        userRepository.insert(new UserAccount(
                userId, userId, userId, "UNSET", "ARGON2ID", "{}", "v1", 1, UserState.ACTIVE,
                clock.instant(), "test-fixture", null));
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(), userId, Role.MANAGER, ScopeKind.MUNICIPALITY,
                municipalityIbge, null, null, clock.instant(), "test-fixture", null, null));
        return userId;
    }

    @Override
    public void close() {
        context.close();
    }
}
