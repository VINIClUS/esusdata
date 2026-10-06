package esusdata.run.worker;

import esusdata.auth.GrantRevalidator;
import esusdata.auth.JdbcGrantRepository;
import esusdata.auth.JdbcUserRepository;
import esusdata.auth.ScopeResolver;
import esusdata.auth.model.Grant;
import esusdata.auth.model.GrantRepository;
import esusdata.auth.model.Role;
import esusdata.auth.model.ScopeKind;
import esusdata.auth.model.UserAccount;
import esusdata.auth.model.UserRepository;
import esusdata.auth.model.UserState;
import esusdata.config.SqliteConfig;
import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.result.JdbcEvidenceRepository;
import esusdata.result.JdbcExtractionManifestRepository;
import esusdata.result.JdbcResultRepository;
import esusdata.result.JdbcResultStagingArea;
import esusdata.result.PublicationService;
import esusdata.result.ReproducibilityCheck;
import esusdata.result.model.EvidencePage;
import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.EvidenceRepository;
import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import esusdata.result.model.ResultStagingArea;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionListener;
import esusdata.run.acquisition.CancellationSignal;
import esusdata.run.acquisition.InProcessAcquisition;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.FileExtractStore;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.JdbcAcquisitionGuardStore;
import esusdata.run.job.JdbcJobRepository;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.job.RetryPolicy;
import esusdata.source.JdbcSourceRepository;
import esusdata.source.SourceRepository;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.source.pec.CompatibilityCatalog;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecDataSourceFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Assembles the job-runner/result-store/identity-access beans as plain objects over a real {@code
 * SqliteConfig}-migrated database — the same pattern {@code SqliteConfigTest}
 * uses. Deliberately does not register {@code RunConfig}: that class wires a
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

    /** The registry the executors of this fixture gate by: the shipped one unless a test passes packs. */
    public ReleaseGateRegistry gateRegistry = ReleaseGateRegistry.bundled();

    /** The compatibility matrix {@link #replay} checks the source's capabilities against (Portão C). */
    public PecCompatibilityMatrix replayMatrix = PecCompatibilityMatrix.fromClasspathResource();

    public final ReproducibilityCheck reproducibilityCheck;
    public final PublicationService publicationService;
    public final RunExecutor executor;
    public final UserRepository userRepository;
    public final GrantRepository grantRepository;
    public final GrantRevalidator grantRevalidator;
    public final CancellationRegistry cancellationRegistry = new CancellationRegistry();
    public final RetryPolicy retryPolicy = new RetryPolicy(Duration.ofMillis(1), Duration.ofSeconds(1));
    public final Duration liveAcquisitionCooldownMargin = Duration.ofMinutes(1);
    public final Path extractsDir;
    public final Clock clock;

    public JobRunnerTestFixture(Path dataDir, Clock clock) {
        this(dataDir, clock, null, null);
    }

    /**
     * @param pecDataSourceFactory when non-null, used instead of the default no-destination-
     *     allowed factory — lets a live-acquisition test point {@code runLive} at a real
     *     Testcontainers PostgreSQL instance.
     */
    public JobRunnerTestFixture(Path dataDir, Clock clock, PecDataSourceFactory pecDataSourceFactory) {
        this(dataDir, clock, pecDataSourceFactory, null);
    }

    /**
     * @param compatibilityCatalog when non-null, used instead of the real {@code
     *     JdbcCompatibilityCatalog} — mirrors {@code IndividualEncounterModalityCapability
     *     .stream}'s own seam so a synthetic PostgreSQL fixture can supply probes for testing.
     */
    public JobRunnerTestFixture(
            Path dataDir,
            Clock clock,
            PecDataSourceFactory pecDataSourceFactory,
            CompatibilityCatalog compatibilityCatalog) {
        this.clock = clock;
        this.extractsDir = dataDir.resolve("extracts");

        context = new AnnotationConfigApplicationContext();
        context.register(SqliteConfig.class);
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("observatorio.data.directory", dataDir.toString())));
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
                jdbc,
                transactionTemplate,
                jobRepository,
                extractionManifestRepository,
                reproducibilityCheck,
                extractsDir,
                grantRevalidator);
        // No destination is ever allow-listed by default — extract-only tests never call
        // PecDataSourceFactory.open, and EnvFileSecretResolver only touches this path lazily,
        // inside resolve(), which live-acquisition tests exercise with their own real secret file.
        PecDataSourceFactory factory = pecDataSourceFactory != null
                ? pecDataSourceFactory
                : new PecDataSourceFactory(
                        new AllowedDestinations(Set.of()), new EnvFileSecretResolver(dataDir.resolve("unused.env")));
        Acquisition acquisitionPort = compatibilityCatalog != null
                ? new InProcessAcquisition(factory, extractsDir, clock, compatibilityCatalog)
                : new InProcessAcquisition(factory, extractsDir, clock);
        executor = new RunExecutor(
                new FileExtractStore(extractsDir),
                jobRepository,
                stagingArea,
                publicationService,
                "test-build",
                clock,
                grantRevalidator,
                sourceRepository,
                acquisitionPort,
                acquisitionGuard(),
                liveAcquisitionCooldownMargin);
    }

    /**
     * An executor over this fixture's database and extracts that resolves {@code rules} — any
     * {@link IndicatorRule} instance, registered in the release or not — before the registry (ADR
     * 0030), against the given compatibility matrix.
     */
    public RunExecutor executor(PecCompatibilityMatrix matrix, Acquisition acquisition, IndicatorRule... rules) {
        return new RunExecutor(
                new FileExtractStore(extractsDir),
                jobRepository,
                stagingArea,
                publicationService,
                "test-build",
                clock,
                grantRevalidator,
                sourceRepository,
                acquisition,
                acquisitionGuard(),
                liveAcquisitionCooldownMargin,
                new CapabilityEligibility(matrix),
                (pack, ruleVersion) -> {
                    for (IndicatorRule rule : rules) {
                        if (rule.descriptor().id().equals(pack)
                                && rule.descriptor().ruleVersion().equals(ruleVersion)) {
                            return rule;
                        }
                    }
                    return IndicatorRuleRegistry.require(pack, ruleVersion);
                },
                gateRegistry);
    }

    /**
     * ENG-19 for a pack (ADR 0030): enqueues an IMMUTABLE_EXTRACT job of {@code rule} over {@code
     * extract} for {@code competencia}, takes it, and runs it to publication — no PEC involved.
     * {@code principal} must hold RUN_INDICATOR in the extract's municipality ({@link
     * #registerPrincipal}).
     */
    public RunExecutor.RunOutcome replay(
            ExtractionManifest extract, IndicatorRule rule, YearMonth competencia, String principal)
            throws IOException {
        String jobId = "job-replay-" + UUID.randomUUID();
        jobRepository.enqueue(new EnqueueRequest(
                jobId,
                "run-" + jobId,
                extract.municipalityIbge(),
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                competencia.toString(),
                1,
                extract.sourceId(),
                extract.extractionId(),
                principal,
                null,
                null,
                null,
                null,
                clock.instant()));
        Job job = jobRepository.acquireNext("proc-replay", clock.instant()).orElseThrow();
        if (!job.jobId().equals(jobId)) {
            throw new IllegalStateException("another queued job was taken first: " + job.jobId());
        }
        return executor(replayMatrix, new UnusedAcquisition(), rule)
                .runFromExtract(context(job), new CancellationToken());
    }

    /** The {@code RunContext} the worker builds for {@code job}. */
    public static RunExecutor.RunContext context(Job job) {
        return new RunExecutor.RunContext(
                job.jobId(),
                job.runId(),
                job.sourceId(),
                job.executionGeneration(),
                job.processInstanceId(),
                job.extractionId(),
                job.municipalityIbge(),
                job.referencePeriod(),
                job.indicatorPack(),
                job.ruleVersion(),
                job.idempotencyPrincipal());
    }

    /** The published result, read as the API reads it. */
    public PublishedResult published(String resultId, String municipalityIbge) {
        return resultRepository.findByIdInScope(resultId, municipalityIbge).orElseThrow();
    }

    /** Every evidence row of a published result, in {@code seq} order. */
    public List<EvidenceRecord> evidence(String resultId, String municipalityIbge) {
        JdbcEvidenceRepository evidence = new JdbcEvidenceRepository(jdbc);
        List<EvidenceRecord> rows = new ArrayList<>();
        Long cursor = null;
        do {
            EvidencePage page = evidence.page(resultId, municipalityIbge, cursor, EvidenceRepository.MAX_PAGE_SIZE);
            rows.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return List.copyOf(rows);
    }

    /** Replays never acquire: an acquisition here is a test bug. */
    private static final class UnusedAcquisition implements Acquisition {
        @Override
        public ExtractionManifest acquire(
                AcquisitionCommand command, CancellationSignal cancellation, AcquisitionListener listener) {
            throw new IllegalStateException("a replay never acquires from the PEC");
        }
    }

    public JobRecovery jobRecovery() {
        return new JobRecovery(
                jobRepository,
                transactionTemplate,
                stagingArea,
                acquisitionGuard(),
                retryPolicy,
                clock,
                liveAcquisitionCooldownMargin);
    }

    public AcquisitionGuard acquisitionGuard() {
        return new AcquisitionGuard(new JdbcAcquisitionGuardStore(jdbc), clock);
    }

    public JobWorker worker(String processInstanceId) {
        return new JobWorker(
                jobRepository,
                executor,
                stagingArea,
                cancellationRegistry,
                retryPolicy,
                clock,
                processInstanceId,
                Duration.ofMillis(50));
    }

    /** Registers a default source row so FK constraints on jobs/extraction_manifests/results are satisfiable. */
    public String registerSource(String sourceId, String municipalityIbge) {
        sourceRepository.upsert(new SourceRecord(
                sourceId,
                1,
                "PEC_POSTGRESQL",
                "PRONTUARIO",
                "PRIMARY",
                "127.0.0.1", // NOPMD - AvoidUsingHardCodedIP: loopback test server
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                municipalityIbge,
                "5.4.37",
                "PEC_DW",
                Instant.EPOCH.toString()));
        return sourceId;
    }

    /**
     * Registers an ACTIVE user with a MANAGER grant scoped to {@code municipalityIbge} — MANAGER
     * is the only seeded role carrying {@code run_indicator} (see V3's {@code role_permissions}).
     * Returns the user id, to be used as {@code EnqueueRequest.idempotencyPrincipal}.
     */
    public String registerPrincipal(String userId, String municipalityIbge) {
        userRepository.insert(new UserAccount(
                userId,
                userId,
                userId,
                "UNSET",
                "ARGON2ID",
                "{}",
                "v1",
                1,
                UserState.ACTIVE,
                clock.instant(),
                "test-fixture",
                null));
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(),
                userId,
                Role.MANAGER,
                ScopeKind.MUNICIPALITY,
                municipalityIbge,
                null,
                null,
                clock.instant(),
                "test-fixture",
                null,
                null));
        return userId;
    }

    @Override
    public void close() {
        context.close();
    }
}
