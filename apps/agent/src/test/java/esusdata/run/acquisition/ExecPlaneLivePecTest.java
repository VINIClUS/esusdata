package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import esusdata.run.extract.ExtractionManifest;
import esusdata.run.job.CancellationToken;
import esusdata.run.job.JobCancelledException;
import esusdata.run.worker.FailureClassifier;
import esusdata.source.JdbcSourceDiagnostics;
import esusdata.source.SourceDiagnosticsService;
import esusdata.source.SourceDiagnosticsService.Diagnostics;
import esusdata.source.SourceDiagnosticsService.Outcome;
import esusdata.source.SourceRepository;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.IndividualEncounterModalityCapability;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The execution plane against a real PEC, through the production constructor — the packaged
 * compatibility matrix and the real ENG-43 handshake, never a synthetic one. A fingerprint
 * mismatch fails this test closed; it is never worked around by forcing {@code proceed}.
 *
 * <p>The source's true identity comes from its secret file, not from this class: besides the
 * {@code PEC_DB_*} connection keys it must carry {@code PEC_SOURCE_ID}, {@code PEC_VERSION} (the
 * version the installation actually runs, never one borrowed to reach a matrix entry) and
 * {@code PEC_MUNICIPALITY_IBGE}. The file is {@link LivePecAssumptions#ENV_FILE} unless
 * {@code -Dobservatorio.execution-plane.live-pec.env-file} points elsewhere.
 *
 * <p><b>Gate:</b> its own opt-in {@code -Dobservatorio.execution-plane.live-pec=true}, on top of
 * {@code -Dobservatorio.execution-plane.binary} and the secret file + a reachable tunnel + a
 * real login. The binary property alone is what the README's ordinary
 * {@code mvn verify} passes — that must never, by itself, send a failed login and a cancelled
 * read to the production PEC. Extracts land only in this test's {@code @TempDir} — they carry
 * patient data and are deleted with it.
 */
class ExecPlaneLivePecTest {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneLivePecTest.class);

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";

    @TempDir
    Path extractsDir;

    private String realBinary;
    private Path envFile;
    private Map<String, String> env;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: touches the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                realBinary != null && !realBinary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(
                Files.isExecutable(Path.of(realBinary)), "Skipping: " + realBinary + " is not an executable file");
        String configuredEnvFile = System.getProperty(ENV_FILE_PROPERTY);
        envFile = configuredEnvFile == null || configuredEnvFile.isBlank()
                ? LivePecAssumptions.ENV_FILE
                : Path.of(configuredEnvFile);
        Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
        env = Files.readAllLines(envFile).stream()
                .filter(line -> line.contains("="))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim()));
        for (String key : List.of("PEC_SOURCE_ID", "PEC_VERSION", "PEC_MUNICIPALITY_IBGE")) {
            Assumptions.assumeTrue(
                    env.get(key) != null && !env.get(key).isBlank(),
                    "Skipping: " + envFile + " has no " + key + " — the source's true identity is required");
        }
        Assumptions.assumeTrue(
                LivePecAssumptions.isReachable(env.get("PEC_DB_HOST"), port()),
                "Skipping: " + env.get("PEC_DB_HOST") + ":" + port() + " not reachable — tunnel likely down");
        // The tunnel's local port accepts TCP even when the PEC's PostgreSQL behind it is down —
        // only a real login proves there is a server to test against.
        Assumptions.assumeTrue(canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
    }

    // javac's try lint: the resource is held for the block's scope, never read.
    @SuppressWarnings("try")
    private boolean canLogIn() {
        try (Connection ignored = openCheckConnection()) {
            return true;
        } catch (java.sql.SQLException unreachable) {
            return false;
        }
    }

    private Connection openCheckConnection() throws java.sql.SQLException {
        char[] password = new EnvFileSecretResolver(envFile).resolve("PEC_DB_PASSWORD");
        try {
            return DriverManager.getConnection(
                    "jdbc:postgresql://" + env.get("PEC_DB_HOST") + ":" + port() + "/" + env.get("PEC_DB_NAME")
                            + "?ApplicationName=observatorio-aps-livetest-check&readOnly=true&loginTimeout=5",
                    env.get("PEC_DB_USER"),
                    new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private int port() {
        return Integer.parseInt(env.get("PEC_DB_PORT"));
    }

    private ExecPlaneAcquisition adapter(PecSecretResolver secretResolver) {
        return new ExecPlaneAcquisition(
                List.of(realBinary),
                secretResolver,
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(env.get("PEC_DB_HOST"), port()))),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(10));
    }

    private PecSourceIdentity identity() {
        return new PecSourceIdentity(env.get("PEC_SOURCE_ID"), env.get("PEC_VERSION"), "PEC_DW", "PRONTUARIO");
    }

    /**
     * The whole history (~294k rows on CT 133), not one competência's ~10k: against a container,
     * a cancel sent at the first {@code progress} landed only after up to ~90k more rows, so a
     * single month would already be fully on the wire and never exercise the cancel.
     * Statement/duration ceilings are tighter than the default so a cancel that doesn't land still
     * stops within 20s.
     */
    private AcquisitionCommand wholeHistoryCommand(String extractionId) {
        ReadBudget budget = new ReadBudget(
                2,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                20_000,
                10_000,
                20_000,
                500_000,
                20_000,
                ReadBudget.DEFAULT_MAX_PAYLOAD_BYTES,
                ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES);
        return command(extractionId, budget, LocalDate.of(2000, 1, 1), LocalDate.of(2027, 1, 1));
    }

    private AcquisitionCommand command(
            String extractionId, ReadBudget budget, LocalDate periodStart, LocalDate periodEndExclusive) {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        env.get("PEC_SOURCE_ID"),
                        env.get("PEC_DB_HOST"),
                        port(),
                        env.get("PEC_DB_NAME"),
                        env.get("PEC_DB_USER"),
                        "PEC_DB_PASSWORD",
                        env.get("PEC_MUNICIPALITY_IBGE")),
                identity(),
                budget,
                extractionId,
                periodStart,
                periodEndExclusive,
                "America/Sao_Paulo");
    }

    private static class RecordingListener implements AcquisitionListener {
        final AtomicInteger progressCount = new AtomicInteger();
        final List<String> uncertainReasons = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress() {
            progressCount.incrementAndGet();
        }

        @Override
        public void onUncertainOutcome(String reason) {
            uncertainReasons.add(reason);
        }
    }

    /**
     * The evidence half of ENG-43 for this installation: every object of the matrix entry selected
     * by the source's true identity, fingerprinted over JDBC ({@link JdbcCompatibilityCatalog}) and
     * printed next to the pinned value. The Rust child's own measurement of the same objects is
     * logged by {@code ExecPlaneAcquisition} during {@link #acquiresOneMonthEndToEndThroughTheRustChild}.
     */
    @Test
    void fingerprintsOfTheRealPecMatchTheMatrixEntryForItsDeclaredVersion() throws Exception {
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        try (Connection c = openCheckConnection()) {
            String postgresVersion = catalog.postgresVersion(c);
            PecCompatibilityMatrix.Entry entry = PecCompatibilityMatrix.fromClasspathResource()
                    .findExact(
                            IndividualEncounterModalityCapability.CAPABILITY,
                            IndividualEncounterModalityCapability.ADAPTER_VERSION,
                            identity(),
                            postgresVersion);
            log.info("fingerprints: source=" + identity().sourceId() + " PEC="
                    + identity().pecVersion() + " PostgreSQL=" + postgresVersion);
            for (Map.Entry<String, String> expected : entry.objectFingerprints().entrySet()) {
                String object = expected.getKey();
                String observed =
                        catalog.fingerprint(c, object, entry.objectColumns().get(object));
                log.info("fingerprint " + object + " matrix=" + expected.getValue() + " jdbc=" + observed);
                assertThat(observed).as(object).isEqualTo(expected.getValue());
            }
        }
    }

    /**
     * One competência, not the whole history — a full sorted read of the fact table is the
     * cancel test's job, and this server is in clinical use.
     */
    @Test
    void acquiresOneMonthEndToEndThroughTheRustChild() {
        RecordingListener listener = new RecordingListener();

        ExtractionManifest manifest = adapter(new EnvFileSecretResolver(envFile))
                .acquire(
                        command(
                                "live-month",
                                ReadBudget.initialEngineeringProposal(),
                                LocalDate.of(2026, 3, 1),
                                LocalDate.of(2026, 4, 1)),
                        new CancellationToken(),
                        listener);
        log.info("live month: rows=" + manifest.rowCount() + " exclusions=" + manifest.exclusionCount() + " checksum="
                + manifest.checksum());

        assertThat(manifest.sourceId()).isEqualTo(identity().sourceId());
        assertThat(manifest.municipalityIbge()).isEqualTo(env.get("PEC_MUNICIPALITY_IBGE"));
        assertThat(manifest.queryChecksum()).isEqualTo(IndividualEncounterModalityCapability.QUERY_CHECKSUM);
        assertThat(manifest.rowCount()).isPositive();
        assertThat(listener.uncertainReasons).isEmpty();
        assertThat(extractsDir.resolve("live-month.jsonl.gz")).exists();
    }

    /**
     * Exactly one failed login against the production server (it lands in its auth log) — the
     * JDBC-parity half of this is proven against a container by {@code
     * ExecPlaneDifferentialLiveTest}, not repeated here.
     */
    @Test
    void wrongPasswordIsAnAuthenticationFailureWithoutCooldown() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchThrowable(() -> adapter(secretRef -> "definitely-not-the-password".toCharArray())
                .acquire(wholeHistoryCommand("live-auth"), new CancellationToken(), listener));

        assertThat(FailureClassifier.classify(failure).code()).isEqualTo("SOURCE_AUTHENTICATION_FAILED");
        assertThat(listener.uncertainReasons).isEmpty();
    }

    @Test
    void cancellingAfterRowsWereEmittedStopsTheQueryOnTheRealPec() throws Exception {
        CancellationToken cancellation = new CancellationToken();
        RecordingListener listener = new RecordingListener() {
            @Override
            public void onProgress() {
                super.onProgress();
                cancellation.requestCancel();
            }
        };

        long startedAt = System.nanoTime();
        Throwable failure = catchThrowable(() -> adapter(new EnvFileSecretResolver(envFile))
                .acquire(wholeHistoryCommand("live-cancel"), cancellation, listener));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("live cancel: elapsedMs=" + elapsedMs + " progressMessages=" + listener.progressCount.get());

        assertThat(failure)
                .as("the period streamed in full before the cancel landed (or had < 1000 rows)")
                .isNotNull();
        assertThat(failure).isInstanceOf(JobCancelledException.class);
        assertThat(listener.progressCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(listener.uncertainReasons).singleElement().asString().contains("cancelled cooperatively");
        assertThat(extractsDir.resolve("live-cancel.jsonl.gz")).doesNotExist();
        assertThat(activeObservatorioQueries()).isZero();
    }

    /**
     * ADR 0017's gate against the production PEC: the execution plane's diagnostic returns the same
     * {@link Diagnostics} as the pgJDBC one it replaced. One login each; the wrong-password case
     * below adds one failed login per path, nothing more.
     */
    @Test
    void diagnosticMatchesTheJdbcReference() {
        Diagnostics reference =
                jdbcDiagnostics(new EnvFileSecretResolver(envFile)).test(env.get("PEC_SOURCE_ID"));
        Diagnostics candidate =
                execPlaneDiagnostics(new EnvFileSecretResolver(envFile)).test(env.get("PEC_SOURCE_ID"));

        assertThat(reference.outcome()).isEqualTo(Outcome.CONNECTED);
        assertThat(candidate).isEqualTo(reference);
    }

    @Test
    void wrongPasswordDiagnosticMatchesTheJdbcReference() {
        PecSecretResolver wrong = secretRef -> "definitely-not-the-password".toCharArray();
        Diagnostics reference = jdbcDiagnostics(wrong).test(env.get("PEC_SOURCE_ID"));
        Diagnostics candidate = execPlaneDiagnostics(wrong).test(env.get("PEC_SOURCE_ID"));

        assertThat(reference.outcome()).isEqualTo(Outcome.SOURCE_AUTHENTICATION_FAILED);
        assertThat(candidate).isEqualTo(reference);
    }

    private SourceRepository singleSourceRepository() {
        SourceRecord source = new SourceRecord(
                env.get("PEC_SOURCE_ID"),
                1,
                "PEC_POSTGRESQL",
                "PRONTUARIO",
                "PRIMARY",
                env.get("PEC_DB_HOST"),
                port(),
                env.get("PEC_DB_NAME"),
                env.get("PEC_DB_USER"),
                "PEC_DB_PASSWORD",
                env.get("PEC_MUNICIPALITY_IBGE"),
                env.get("PEC_VERSION"),
                "PEC_DW",
                "2026-09-24T00:00:00Z");
        return new SourceRepository() {
            @Override
            public void upsert(SourceRecord ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<SourceRecord> findById(String id) {
                return Optional.of(source).filter(candidate -> candidate.id().equals(id));
            }
        };
    }

    private AllowedDestinations allowedDestinations() {
        return new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(env.get("PEC_DB_HOST"), port())));
    }

    private JdbcSourceDiagnostics jdbcDiagnostics(PecSecretResolver secretResolver) {
        return new JdbcSourceDiagnostics(
                singleSourceRepository(),
                allowedDestinations(),
                new PecDataSourceFactory(allowedDestinations(), secretResolver));
    }

    private SourceDiagnosticsService execPlaneDiagnostics(PecSecretResolver secretResolver) {
        return new SourceDiagnosticsService(
                singleSourceRepository(),
                allowedDestinations(),
                new ExecPlaneConnectivityCheck(List.of(realBinary), secretResolver, Duration.ofSeconds(10)));
    }

    private long activeObservatorioQueries() throws Exception {
        try (Connection c = openCheckConnection();
                Statement st = c.createStatement()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (true) {
                long active;
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_stat_activity "
                        + "WHERE application_name = 'observatorio-aps' AND state <> 'idle'")) {
                    rs.next();
                    active = rs.getLong(1);
                }
                if (active == 0 || System.nanoTime() > deadline) {
                    return active;
                }
                Thread.sleep(100);
            }
        }
    }
}
