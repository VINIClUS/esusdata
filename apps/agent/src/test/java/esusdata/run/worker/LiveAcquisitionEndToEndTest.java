package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.pack.c1.C1Rule;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobCancelledException;
import esusdata.run.job.JobState;
import esusdata.run.job.SourceAcquisitionBlockedException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CompatibilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSourceIdentity;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises {@link RunExecutor#runLive} end to end against a real PostgreSQL connection
 * — grant revalidation, {@link AcquisitionGuard}, the pooled connection, the cancellable {@code
 * IndividualEncounterModalityCapability.stream} overload, extract write/finalize, and the shared
 * compute→stage→publish path {@code runFromExtract} also uses.
 *
 * <p>Pinned to {@code postgres:9.6.13} — the exact version the frozen compatibility contract in
 * {@code contracts/... pec-adapters.json} (packaged at {@code /compatibility/pec-adapters.json})
 * was validated against; the plain {@code postgres:9.6} tag resolves to 9.6.24. Compatibility
 * fingerprinting itself is injected here with the matrix's own recorded values, exactly like
 * {@code IndividualEncounterModalityCapabilityIsolationTest} does — it proves this synthetic
 * fixture is isolated and queryable, not that its schema is byte-identical to the real PEC's (see
 * ENG-37: Testcontainers "não fornece automaticamente um esquema PEC homologado"). Production
 * always constructs {@code InProcessAcquisition} with its two-argument constructor, which always
 * uses the real {@code JdbcCompatibilityCatalog}; only this test injects a substitute, through
 * {@link JobRunnerTestFixture}'s catalog-accepting constructor.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
@Testcontainers
class LiveAcquisitionEndToEndTest {

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:9.6.13")
            .withDatabaseName("esus_fixture")
            .withUsername("fixture_user")
            .withPassword("fixture_password");

    private static final Path FIXTURE_FILE = Path.of("src/test/resources/fixtures/pec_synthetic_fixture.sql");

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    private static boolean fixtureLoaded;

    @BeforeEach
    void setUp() throws Exception {
        loadFixtureOnce();
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

        Set<AllowedDestinations.HostPort> allowed = new HashSet<>();
        allowed.add(new AllowedDestinations.HostPort(PG.getHost(), PG.getMappedPort(5432)));
        for (InetAddress address : InetAddress.getAllByName(PG.getHost())) {
            allowed.add(new AllowedDestinations.HostPort(address.getHostAddress(), PG.getMappedPort(5432)));
        }
        var factory = new PecDataSourceFactory(
                new AllowedDestinations(allowed), secretRef -> "fixture_password".toCharArray());

        // Computed before the fixture (which now wires the Acquisition at construction time,
        // not per-call) — depends only on the packaged matrix and the pinned PG version, not on
        // anything the fixture itself builds.
        var entry = PecCompatibilityMatrix.fromClasspathResource()
                .findExact(
                        "individual_encounter_modality",
                        "0.1.0",
                        new PecSourceIdentity("fixture-a", "5.4.37", "PEC_DW", "PRONTUARIO"),
                        "9.6.13");
        CompatibilityCatalog fixtureCatalog = new CompatibilityCatalog() {
            @Override
            public String postgresVersion(Connection connection) {
                return "9.6.13";
            }

            @Override
            public String fingerprint(Connection connection, String object, List<String> columnsUsed)
                    throws SQLException {
                String fingerprint = entry.objectFingerprints().get(object);
                if (fingerprint == null) {
                    throw new SQLException("No fixture fingerprint for " + object);
                }
                return fingerprint;
            }
        };

        fixture = new JobRunnerTestFixture(dataDir, clock, factory, fixtureCatalog);

        fixture.sourceRepository.upsert(new SourceRecord(
                "fixture-a",
                1,
                "PEC_POSTGRESQL",
                "PRONTUARIO",
                "PRIMARY",
                PG.getHost(),
                PG.getMappedPort(5432),
                "esus_fixture",
                "fixture_user",
                "unused",
                "1100015",
                "5.4.37",
                "PEC_DW",
                Instant.EPOCH.toString()));
        fixture.registerPrincipal("test-principal", "1100015");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private static void loadFixtureOnce() throws Exception {
        if (fixtureLoaded) {
            return;
        }
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                Statement st = c.createStatement()) {
            st.execute(Files.readString(FIXTURE_FILE));
        }
        fixtureLoaded = true;
    }

    private RunExecutor.RunContext liveContext(String jobId, String municipalityIbge) {
        fixture.jobRepository.enqueue(new EnqueueRequest(
                jobId,
                "run-" + jobId,
                municipalityIbge,
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                3,
                "fixture-a",
                null,
                "test-principal",
                null,
                null,
                null,
                null,
                clock.instant()));
        Job acquired =
                fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();
        return new RunExecutor.RunContext(
                acquired.jobId(),
                acquired.runId(),
                acquired.sourceId(),
                acquired.executionGeneration(),
                acquired.processInstanceId(),
                acquired.extractionId(),
                acquired.municipalityIbge(),
                acquired.referencePeriod(),
                acquired.indicatorPack(),
                acquired.ruleVersion(),
                acquired.idempotencyPrincipal());
    }

    @Test
    void acquiresFromPostgresAndPublishesABlockedResultWithExactCounts() throws Exception {
        var context = liveContext("job-live-ok", "1100015");

        var outcome = fixture.executor.runLive(context, new CancellationToken());

        // Municipality A in the fixture: 3 programados, 2 espontaneos (see
        // IndividualEncounterModalityCapabilityIsolationTest) -> numerator 3, denominator 5.
        assertThat(outcome.result().numerator().intValue()).isEqualTo(3);
        assertThat(outcome.result().denominator().intValue()).isEqualTo(5);
        assertThat(outcome.resultId()).isNotNull();
        assertThat(fixture.jobRepository.findById("job-live-ok").orElseThrow().state())
                .isEqualTo(JobState.SUCCEEDED);
        // The extract this run wrote is durable on disk and was read back for the C1 computation
        // — exactly like a replayed IMMUTABLE_EXTRACT run (§1.9.4: from the finalized extract,
        // never kept only in memory), not simply the streamed rows this test method still holds.
        String extractionId = "live-" + context.jobId() + "-g" + context.executionGeneration();
        assertThat(fixture.extractsDir.resolve(extractionId + ".manifest.json")).exists();
    }

    @Test
    void cancellingBeforeAcquisitionAbortsWithNoStagingOrPublication() {
        var context = liveContext("job-live-cancel", "1100015");
        CancellationToken cancellation = new CancellationToken();
        cancellation.requestCancel(); // pre-set: the first per-row poll inside stream() throws.

        assertThatThrownBy(() -> fixture.executor.runLive(context, cancellation))
                .isInstanceOf(JobCancelledException.class);

        assertThat(fixture.jdbc.queryForObject("select count(*) from results", Integer.class))
                .isZero();
        assertThat(fixture.jdbc.queryForObject("select count(*) from result_staging", Integer.class))
                .isZero();
        // The job itself is left RUNNING here — JobWorker (not the executor) is what resolves a
        // JobCancelledException into a terminal state; that path is proven by other tests.
        assertThat(fixture.jobRepository
                        .findById("job-live-cancel")
                        .orElseThrow()
                        .state())
                .isEqualTo(JobState.RUNNING);
    }

    @Test
    void cancellingDuringAcquisitionBlocksTheSourceOnCooldown() {
        // Cancellation only sends a best-effort Statement#cancel() — this run ends without any
        // proof the PostgreSQL backend actually stopped, the same uncertainty an abandoned RUNNING
        // job leaves for JobRecovery. runLive must apply the same ENG-51 cooldown itself, not only
        // on the next process restart.
        var context = liveContext("job-live-cancel-guard", "1100015");
        CancellationToken cancellation = new CancellationToken();
        cancellation.requestCancel();

        assertThatThrownBy(() -> fixture.executor.runLive(context, cancellation))
                .isInstanceOf(JobCancelledException.class);

        var retryContext = liveContext("job-live-cancel-guard-retry", "1100015");
        assertThatThrownBy(() -> fixture.executor.runLive(retryContext, new CancellationToken()))
                .isInstanceOf(SourceAcquisitionBlockedException.class);
    }
}
