package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.config.SqliteDataSourceConfig;
import br.gov.observatorioaps.identityaccess.Grant;
import br.gov.observatorioaps.identityaccess.GrantRepository;
import br.gov.observatorioaps.identityaccess.GrantRevalidator;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.identityaccess.ScopeResolver;
import br.gov.observatorioaps.identityaccess.UserAccount;
import br.gov.observatorioaps.identityaccess.UserRepository;
import br.gov.observatorioaps.identityaccess.UserState;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.PublicationService;
import br.gov.observatorioaps.resultstore.ReproducibilityCheck;
import br.gov.observatorioaps.resultstore.ResultRepository;
import br.gov.observatorioaps.resultstore.ResultStagingArea;
import br.gov.observatorioaps.resultstore.SourceRecord;
import br.gov.observatorioaps.resultstore.SourceRepository;
import br.gov.observatorioaps.sourceconnector.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.EnvFileSecretResolver;
import br.gov.observatorioaps.sourceconnector.PecDataSourceFactory;
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
    final UserRepository userRepository;
    final GrantRepository grantRepository;
    final GrantRevalidator grantRevalidator;
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

        userRepository = new UserRepository(jdbc);
        grantRepository = new GrantRepository(jdbc);
        ScopeResolver scopeResolver = new ScopeResolver(jdbc);
        grantRevalidator = new GrantRevalidator(scopeResolver, userRepository);

        publicationService = new PublicationService(
                jdbc, transactionTemplate, extractionManifestRepository, reproducibilityCheck,
                extractsDir, grantRevalidator);
        // No destination is ever allow-listed here — extract-only tests never call
        // PecDataSourceFactory.open, and EnvFileSecretResolver only touches this path lazily,
        // inside resolve(), which live-acquisition tests exercise with their own real secret file.
        PecDataSourceFactory pecDataSourceFactory = new PecDataSourceFactory(
                new AllowedDestinations(Set.of()), new EnvFileSecretResolver(dataDir.resolve("unused.env")));
        executor = new IndicatorRunExecutor(
                extractsDir, jobRepository, stagingArea, publicationService, "test-build", clock,
                grantRevalidator, sourceRepository, pecDataSourceFactory, acquisitionGuard());
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

    /**
     * Registers an ACTIVE user with a MANAGER grant scoped to {@code municipalityIbge} — MANAGER
     * is the only seeded role carrying {@code run_indicator} (see V3's {@code role_permissions}).
     * Returns the user id, to be used as {@code EnqueueRequest.idempotencyPrincipal}.
     */
    String registerPrincipal(String userId, String municipalityIbge) {
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
