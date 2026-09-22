package br.gov.observatorioaps.sourceconnector.infrastructure.process;

import br.gov.observatorioaps.jobrunner.application.FailureClassifier;
import br.gov.observatorioaps.jobrunner.domain.CancellationToken;
import br.gov.observatorioaps.jobrunner.domain.JobCancelledException;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionCommand;
import br.gov.observatorioaps.sourceconnector.domain.AcquisitionListener;
import br.gov.observatorioaps.sourceconnector.domain.AllowedDestinations;
import br.gov.observatorioaps.sourceconnector.domain.PecConnectionProperties;
import br.gov.observatorioaps.sourceconnector.domain.PecSecretResolver;
import br.gov.observatorioaps.sourceconnector.domain.PecSourceIdentity;
import br.gov.observatorioaps.sourceconnector.domain.ReadBudget;
import br.gov.observatorioaps.sourceconnector.infrastructure.file.EnvFileSecretResolver;
import br.gov.observatorioaps.testsupport.LivePecAssumptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The execution plane against the real PEC on CT 133 (ADR 0003 tunnel, {@code esus_leitura}),
 * through the production constructor — the packaged compatibility matrix and the real ENG-43
 * handshake, never a synthetic one. A fingerprint mismatch fails this test closed; it is never
 * worked around by forcing {@code proceed}.
 *
 * <p><b>Gate:</b> its own opt-in {@code -Dobservatorio.execution-plane.live-pec=true}, on top of
 * {@code -Dobservatorio.execution-plane.binary} and {@link LivePecAssumptions} (secret file +
 * reachable tunnel + a real login). The binary property alone is what the README's ordinary
 * {@code mvn verify} passes — that must never, by itself, send a failed login and a cancelled
 * read to the production PEC. Extracts land only in this test's {@code @TempDir} — they carry
 * patient data and are deleted with it.
 */
class ExecutionPlaneLivePecTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String OPT_IN_PROPERTY = "observatorio.execution-plane.live-pec";
    private static final String MUNICIPALITY_IBGE = "3541307";

    @TempDir
    Path extractsDir;

    private String realBinary;
    private Map<String, String> env;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(OPT_IN_PROPERTY),
                "Skipping: touches the production PEC — opt in with -D" + OPT_IN_PROPERTY + "=true");
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(realBinary != null && !realBinary.isBlank(),
                "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(Files.isExecutable(Path.of(realBinary)),
                "Skipping: " + realBinary + " is not an executable file");
        Assumptions.assumeTrue(Files.exists(LivePecAssumptions.ENV_FILE),
                "Skipping: no dev PEC secret file at " + LivePecAssumptions.ENV_FILE);
        env = Files.readAllLines(LivePecAssumptions.ENV_FILE).stream()
                .filter(line -> line.contains("="))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')).trim(),
                        line -> line.substring(line.indexOf('=') + 1).trim()));
        LivePecAssumptions.assumeReachable(env.get("PEC_DB_HOST"), port());
        // The tunnel's local port accepts TCP even when the PEC's PostgreSQL behind it is down —
        // only a real login proves there is a server to test against.
        Assumptions.assumeTrue(canLogIn(), "Skipping: tunnel is up but the PEC's PostgreSQL is not answering");
    }

    private boolean canLogIn() {
        try (Connection ignored = openCheckConnection()) {
            return true;
        } catch (java.sql.SQLException unreachable) {
            return false;
        }
    }

    private Connection openCheckConnection() throws java.sql.SQLException {
        char[] password = new EnvFileSecretResolver(LivePecAssumptions.ENV_FILE).resolve("PEC_DB_PASSWORD");
        try {
            return DriverManager.getConnection(
                    "jdbc:postgresql://" + env.get("PEC_DB_HOST") + ":" + port() + "/" + env.get("PEC_DB_NAME")
                            + "?ApplicationName=observatorio-aps-livetest-check&readOnly=true&loginTimeout=5",
                    env.get("PEC_DB_USER"), new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private int port() {
        return Integer.parseInt(env.get("PEC_DB_PORT"));
    }

    private SubprocessAcquisitionAdapter adapter(PecSecretResolver secretResolver) {
        return new SubprocessAcquisitionAdapter(
                List.of(realBinary), secretResolver,
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(env.get("PEC_DB_HOST"), port()))),
                extractsDir, Clock.systemUTC(), Duration.ofSeconds(10));
    }

    /**
     * The whole history (~294k rows, discovery doc), not the pilot competência's ~10k: against a
     * container, a cancel sent at the first {@code progress} landed only after up to ~90k more
     * rows, so a single month would already be fully on the wire and never exercise the cancel.
     * Statement/duration ceilings are tighter than the default so a cancel that doesn't land still
     * stops within 20s.
     */
    private AcquisitionCommand command(String extractionId) {
        ReadBudget budget = new ReadBudget(
                2, Duration.ofSeconds(10), Duration.ofSeconds(10), 20_000, 10_000, 20_000,
                500_000, 20_000, ReadBudget.DEFAULT_MAX_PAYLOAD_BYTES, ReadBudget.DEFAULT_MAX_TEMP_FILE_BYTES);
        return new AcquisitionCommand(
                new PecConnectionProperties("pec-ct133-dev", env.get("PEC_DB_HOST"), port(),
                        env.get("PEC_DB_NAME"), env.get("PEC_DB_USER"), "PEC_DB_PASSWORD", MUNICIPALITY_IBGE),
                new PecSourceIdentity("pec-ct133-dev", "5.4.37", "PEC_DW", "PRONTUARIO"),
                budget, extractionId, LocalDate.of(2000, 1, 1), LocalDate.of(2027, 1, 1),
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
     * Exactly one failed login against the production server (it lands in its auth log) — the
     * JDBC-parity half of this is proven against a container by {@code
     * ExecutionPlaneDifferentialLiveTest}, not repeated here.
     */
    @Test
    void wrongPasswordIsAnAuthenticationFailureWithoutCooldown() {
        RecordingListener listener = new RecordingListener();

        Throwable failure = catchThrowable(() -> adapter(secretRef -> "definitely-not-the-password".toCharArray())
                .acquire(command("ct133-auth"), new CancellationToken(), listener));

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
        Throwable failure = catchThrowable(() -> adapter(new EnvFileSecretResolver(LivePecAssumptions.ENV_FILE))
                .acquire(command("ct133-cancel"), cancellation, listener));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        System.out.println("ct133 cancel: elapsedMs=" + elapsedMs + " progressMessages=" + listener.progressCount.get());

        assertThat(failure)
                .as("the period streamed in full before the cancel landed (or had < 1000 rows)")
                .isNotNull();
        assertThat(failure).isInstanceOf(JobCancelledException.class);
        assertThat(listener.progressCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(listener.uncertainReasons).singleElement().asString().contains("cancelled cooperatively");
        assertThat(extractsDir.resolve("ct133-cancel.jsonl.gz")).doesNotExist();
        assertThat(activeObservatorioQueries()).isZero();
    }

    private long activeObservatorioQueries() throws Exception {
        try (Connection c = openCheckConnection(); Statement st = c.createStatement()) {
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
