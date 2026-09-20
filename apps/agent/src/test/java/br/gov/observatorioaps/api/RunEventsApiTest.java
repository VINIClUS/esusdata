package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.Grant;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.10 L397: "a consulta do job é a fonte de verdade" and "reconexão reenvia estado atual" — the
 * fast poll interval below (not the 2000 ms production default) and a short revalidation window
 * keep these tests from being needlessly slow without changing what they prove.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunEventsApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @DynamicPropertySource
    static void fastCadence(DynamicPropertyRegistry registry) {
        registry.add("observatorio.job-runner.poll-interval-ms", () -> "100");
        registry.add("observatorio.security.authorization-revalidation-interval-seconds", () -> "1");
    }

    @Test
    void connectingDeliversCurrentStateImmediatelyAndReconnectingGetsTheSameCurrentState() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertRunningJob(MUNICIPALITY);

        try (Stream<String> first = openEventStream(cookie, jobId)) {
            String data = readNextDataLine(first.iterator());
            assertThat(data).contains("\"state\":\"RUNNING\"").contains("\"jobId\":\"" + jobId + "\"");
        }

        // A second, independent connection must see the SAME current state straight away — the
        // design polls JobRepository fresh on every tick, so nothing about a prior connection can
        // leak into or be required by a new one.
        try (Stream<String> second = openEventStream(cookie, jobId)) {
            String data = readNextDataLine(second.iterator());
            assertThat(data).contains("\"state\":\"RUNNING\"").contains("\"jobId\":\"" + jobId + "\"");
        }
    }

    @Test
    void revokingTheGrantMidStreamEndsItWithinTheRevalidationWindowEvenWithoutTouchingTheSession() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        String grantId = "grant-" + UUID.randomUUID();
        grantRepository.insert(new Grant(
                grantId, manager, Role.MANAGER, ScopeKind.MUNICIPALITY, MUNICIPALITY, null, null,
                clock.instant(), "test-fixture", null, null));
        String cookie = sessionCookie(manager);
        String jobId = insertRunningJob(MUNICIPALITY);

        try (Stream<String> stream = openEventStream(cookie, jobId)) {
            Iterator<String> lines = stream.iterator();
            assertThat(readNextDataLine(lines)).contains("\"state\":\"RUNNING\"");

            // Revoking the GRANT directly — not through AdminController — leaves the session
            // itself perfectly valid (AuthorizationVersionGuard never runs). If the stream still
            // ends, it is because RunEventsController re-checks scope on every revalidation tick,
            // not merely session validity.
            grantRepository.revoke(grantId, clock.instant(), "test-fixture");

            assertThat(drainWithin(lines, Duration.ofSeconds(5)))
                    .as("stream should have been closed by the reauthorization tick")
                    .isTrue();
        }
    }

    @Test
    void remainingWithinScopeKeepsTheStreamAliveAcrossSeveralRevalidationTicks() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertRunningJob(MUNICIPALITY);

        try (Stream<String> stream = openEventStream(cookie, jobId)) {
            Iterator<String> lines = stream.iterator();
            assertThat(readNextDataLine(lines)).contains("\"state\":\"RUNNING\"");

            // No revocation this time — the heartbeat sent on every revalidation tick must keep
            // arriving, proving the earlier test's closure was caused by the revoke, not by
            // anything else that could end a stream.
            for (int i = 0; i < 3; i++) {
                assertThat(readNextCommentLine(lines)).isEqualTo("keep-alive");
            }
        }
    }

    @Test
    void closingStreamsPromptlyReleasesTheConcurrencyLimitSlot() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertRunningJob(MUNICIPALITY);

        // SseConnectionLimiter's per-user cap is 4. Opening and closing that many streams
        // SEQUENTIALLY, then successfully opening a fifth, is only possible if each closed
        // connection's onCompletion/onError actually ran and released its slot — registering
        // those callbacks is not evidence they fire.
        for (int i = 0; i < 4; i++) {
            try (Stream<String> stream = openEventStream(cookie, jobId)) {
                readNextDataLine(stream.iterator());
            }
            Thread.sleep(2000);
        }

        try (Stream<String> fifth = openEventStream(cookie, jobId)) {
            assertThat(readNextDataLine(fifth.iterator())).contains("\"state\":\"RUNNING\"");
        }
    }

    @Test
    void stayingConnectedDoesNotAdvanceLastInteractiveAt() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String rawToken = rawSessionToken(manager);
        String cookie = SessionCookie.NAME + "=" + rawToken;
        String jobId = insertRunningJob(MUNICIPALITY);
        String sessionId = sha256Hex(rawToken);

        String before = jdbc.queryForObject(
                "select last_interactive_at from sessions where session_id = ?", String.class, sessionId);

        try (Stream<String> stream = openEventStream(cookie, jobId)) {
            Iterator<String> lines = stream.iterator();
            // The handshake itself goes through SessionAuthenticationFilter like any other
            // request — this is what proves it took the non-interactive path.
            readNextDataLine(lines);
        }

        String after = jdbc.queryForObject(
                "select last_interactive_at from sessions where session_id = ?", String.class, sessionId);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void terminalStreamWaitsForTheFinalAttemptBeforeClosing() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertTerminalJobWithoutAttempt(MUNICIPALITY);

        try (Stream<String> stream = openEventStream(cookie, jobId)) {
            // The job state becomes terminal before JobWorker records the final attempt. The
            // stream must not close with the incomplete response from that small visibility gap.
            Thread.sleep(500);
            insertAttempt(jobId, "SUCCEEDED");

            String data = readNextDataLine(stream.iterator());
            assertThat(data).contains("\"state\":\"SUCCEEDED\"")
                    .contains("\"attempts\":[{\"attempt\":1")
                    .contains("\"outcome\":\"SUCCEEDED\"");
        }
    }

    @Test
    void queuedStreamReemitsWhenTheRetryAttemptAppears() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertQueuedJobWithoutAttempt(MUNICIPALITY);

        try (Stream<String> stream = openEventStream(cookie, jobId)) {
            Iterator<String> lines = stream.iterator();
            assertThat(readNextDataLine(lines)).contains("\"state\":\"QUEUED\"")
                    .contains("\"attempts\":[]");

            // JobWorker requeues before recording the transiently failed attempt. The stream must
            // emit again when that history row appears even though the job snapshot is unchanged.
            insertAttempt(jobId, "FAILED_TRANSIENT");

            assertThat(readNextDataLine(lines)).contains("\"state\":\"QUEUED\"")
                    .contains("\"attempts\":[{\"attempt\":1")
                    .contains("\"outcome\":\"FAILED_TRANSIENT\"");
        }
    }

    @Test
    void terminalStreamsWithoutFinalAttemptsEventuallyReleaseTheirSlots() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        List<Stream<String>> streams = new ArrayList<>();

        try {
            for (int i = 0; i < 4; i++) {
                streams.add(openEventStream(cookie, insertTerminalJobWithoutAttempt(MUNICIPALITY)));
            }

            // A failed final-attempt write must not hold all four per-user slots until the
            // emitter's 30-minute timeout. The controller's bounded fallback should close them.
            Thread.sleep(6500);

            try (Stream<String> replacement = openEventStream(cookie, insertRunningJob(MUNICIPALITY))) {
                assertThat(readNextDataLine(replacement.iterator())).contains("\"state\":\"RUNNING\"");
            }
        } finally {
            streams.forEach(Stream::close);
        }
    }

    @Test
    void aJobAlreadyTerminalAtConnectClosesAfterOneEventAndDoesNotLeakTheScheduledPollOrTheSlot()
            throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        // Repeated well past SseConnectionLimiter's per-user cap (4): only possible if a
        // terminal-at-connect job's single tick reliably cancels its own scheduled poll and
        // releases its slot, rather than leaking a task that polls forever (each execution
        // throwing on an already-completed emitter, invisibly, since it never touches the limiter
        // again).
        for (int i = 0; i < 6; i++) {
            String jobId = insertTerminalJob(MUNICIPALITY);
            try (Stream<String> stream = openEventStream(cookie, jobId)) {
                assertThat(readNextDataLine(stream.iterator())).contains("\"state\":\"SUCCEEDED\"");
            }
            Thread.sleep(150);
        }

        String finalJobId = insertRunningJob(MUNICIPALITY);
        try (Stream<String> seventh = openEventStream(cookie, finalJobId)) {
            assertThat(readNextDataLine(seventh.iterator())).contains("\"state\":\"RUNNING\"");
        }
    }

    @Test
    void aFifthConcurrentStreamForTheSameUserIsRefusedWith503() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String jobId = insertRunningJob(MUNICIPALITY);

        // All four held open (not closed) so the fifth genuinely contends for the cap, not merely
        // fails to observe a released slot — the counterpart to the round-trip test above, which
        // only proves the limiter doesn't wrongly refuse.
        try (Stream<String> s1 = openEventStream(cookie, jobId);
                Stream<String> s2 = openEventStream(cookie, jobId);
                Stream<String> s3 = openEventStream(cookie, jobId);
                Stream<String> s4 = openEventStream(cookie, jobId)) {
            readNextDataLine(s1.iterator());
            readNextDataLine(s2.iterator());
            readNextDataLine(s3.iterator());
            readNextDataLine(s4.iterator());

            HttpResponse<Stream<String>> fifth = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/events"))
                            .header("Cookie", cookie)
                            .timeout(Duration.ofSeconds(20))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofLines());
            assertThat(fifth.statusCode()).isEqualTo(503);
        }
    }

    private String insertTerminalJob(String municipalityIbge) {
        String jobId = insertTerminalJobWithoutAttempt(municipalityIbge);
        insertAttempt(jobId, "SUCCEEDED");
        return jobId;
    }

    private String insertTerminalJobWithoutAttempt(String municipalityIbge) {
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, municipalityIbge);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id, finished_at)
                VALUES (?,?,?,?,?,?, 'SUCCEEDED', 1, 3, 'proc-test-owns-nothing', 1, ?, ?, ?)
                """, jobId, "run-" + jobId, municipalityIbge, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", Instant.now().toString(), sourceId, Instant.now().toString());
        return jobId;
    }

    private String insertQueuedJobWithoutAttempt(String municipalityIbge) {
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, municipalityIbge);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, next_attempt_at, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'QUEUED', 1, 3, NULL, 1, ?, ?, ?)
                """, jobId, "run-" + jobId, municipalityIbge, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", Instant.now().plusSeconds(3600).toString(), Instant.now().toString(), sourceId);
        return jobId;
    }

    private void insertAttempt(String jobId, String outcome) {
        jdbc.update("""
                INSERT INTO job_attempts (job_id, attempt, process_instance_id, execution_generation,
                    started_at, finished_at, outcome, failure_code, failure_detail)
                VALUES (?, 1, 'proc-test-owns-nothing', 1, ?, ?, ?, NULL, NULL)
                """, jobId, Instant.now().toString(), Instant.now().toString(), outcome);
    }

    private String insertRunningJob(String municipalityIbge) {
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, municipalityIbge);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'RUNNING', 1, 3, 'proc-test-owns-nothing', 1, ?, ?)
                """, jobId, "run-" + jobId, municipalityIbge, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", Instant.now().toString(), sourceId);
        return jobId;
    }

    private Stream<String> openEventStream(String cookie, String jobId) throws Exception {
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/events"))
                        .header("Cookie", cookie)
                        .timeout(Duration.ofSeconds(20))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private String readNextDataLine(Iterator<String> lines) throws Exception {
        return readNextLineWithPrefix(lines, "data:");
    }

    private String readNextCommentLine(Iterator<String> lines) throws Exception {
        return readNextLineWithPrefix(lines, ":");
    }

    private String readNextLineWithPrefix(Iterator<String> lines, String prefix) throws Exception {
        return withTimeout(() -> {
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
            throw new AssertionError("stream ended before a \"" + prefix + "\" line arrived");
        }, Duration.ofSeconds(10));
    }

    /**
     * @return {@code true} if the stream closed before the deadline — either gracefully (no more
     *     lines) or abruptly ({@code completeWithError} on the server tears down the connection
     *     mid-read, which the JDK HTTP client surfaces to the reader as an {@code IOException},
     *     not a clean EOF).
     */
    private boolean drainWithin(Iterator<String> lines, Duration timeout) throws Exception {
        try {
            return withTimeout(() -> {
                try {
                    while (lines.hasNext()) {
                        lines.next();
                    }
                    return true;
                } catch (java.io.UncheckedIOException e) {
                    return true;
                }
            }, timeout);
        } catch (TimeoutException e) {
            return false;
        }
    }

    private <T> T withTimeout(Callable<T> task, Duration timeout) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(task).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
