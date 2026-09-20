package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.identityaccess.ScopeResolver;
import br.gov.observatorioaps.identityaccess.SessionService;
import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code GET /runs/{id}/events} — §1.10 L397: "a consulta do job é a fonte de verdade" and, with
 * no replay in the MVP, "reconexão reenvia estado atual". Implemented as a scheduler polling
 * {@link JobRepository#findById} rather than an in-process event bus: it never keeps a SQLite
 * transaction open for the life of the stream, never loses an event published between publish and
 * subscribe, and keeps {@code jobrunner} entirely free of Spring Web (see {@code
 * ModuleBoundaryTest}).
 *
 * <p>One scheduled task per connection drives BOTH cadences: every tick re-reads the job
 * (progress, at {@code poll-interval-ms}); every Nth tick also revalidates session and scope (at
 * {@code authorizationRevalidationIntervalSeconds}). A single thread-of-control per emitter avoids
 * ever needing two threads to call {@code SseEmitter} methods on the same emitter concurrently —
 * behavior Boot 4.1's {@code SseEmitter} was not empirically verified for in this session.
 */
@RestController
class RunEventsController {

    private static final Logger log = LoggerFactory.getLogger(RunEventsController.class);
    private static final Set<JobState> TERMINAL = EnumSet.of(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED);
    private static final long EMITTER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long TERMINAL_ATTEMPT_WAIT_MS = TimeUnit.SECONDS.toMillis(5);

    private final JobRepository jobRepository;
    private final RunResponseFactory responseFactory;
    private final ApiAuthorization authorization;
    private final ScopeResolver scopeResolver;
    private final SessionService sessionService;
    private final SseConnectionLimiter limiter;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final long pollIntervalMs;
    private final long reauthEveryNTicks;
    private final long terminalAttemptWaitTicks;

    RunEventsController(
            JobRepository jobRepository, RunResponseFactory responseFactory, ApiAuthorization authorization,
            ScopeResolver scopeResolver, SessionService sessionService, SseConnectionLimiter limiter,
            ScheduledExecutorService sseScheduler, Clock clock,
            @Value("${observatorio.job-runner.poll-interval-ms:2000}") long pollIntervalMs,
            @Value("${observatorio.security.authorization-revalidation-interval-seconds:30}")
                    long authorizationRevalidationIntervalSeconds) {
        this.jobRepository = jobRepository;
        this.responseFactory = responseFactory;
        this.authorization = authorization;
        this.scopeResolver = scopeResolver;
        this.sessionService = sessionService;
        this.limiter = limiter;
        this.scheduler = sseScheduler;
        this.clock = clock;
        this.pollIntervalMs = pollIntervalMs;
        this.reauthEveryNTicks = Math.max(1,
                (authorizationRevalidationIntervalSeconds * 1000) / Math.max(1, pollIntervalMs));
        long effectivePollIntervalMs = Math.max(1, pollIntervalMs);
        this.terminalAttemptWaitTicks = Math.max(1,
                (TERMINAL_ATTEMPT_WAIT_MS + effectivePollIntervalMs - 1) / effectivePollIntervalMs);
    }

    @GetMapping(value = "/api/v1/runs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new ApiNotFoundException("unknown job: " + id));
        authorization.requireObjectScope(session, Permission.RUN_INDICATOR, job.municipalityIbge());

        // Captured into locals: the security context tied to this request thread does not survive
        // into the scheduled task, which runs on a different thread entirely.
        String sessionId = session.sessionId();
        String userId = session.userId();
        String municipalityIbge = job.municipalityIbge();

        limiter.acquire(userId);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicReference<ScheduledFuture<?>> futureHolder = new AtomicReference<>();
        AtomicInteger tick = new AtomicInteger();
        AtomicInteger terminalAttemptWaitTick = new AtomicInteger();
        AtomicReference<JobSnapshot> lastSent = new AtomicReference<>();
        // Guards against completion callbacks running more than once for the same connection —
        // Spring runs onCompletion after onTimeout/onError too, and a double release would drift
        // SseConnectionLimiter's counters below the true number of open connections.
        AtomicBoolean stopped = new AtomicBoolean();

        Runnable onDone = () -> {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> future = futureHolder.get();
            if (future != null) {
                future.cancel(false);
            }
            limiter.release(userId);
        };
        emitter.onCompletion(onDone);
        emitter.onTimeout(() -> {
            onDone.run();
            emitter.complete();
        });
        emitter.onError(e -> onDone.run());

        // scheduleWithFixedDelay, not scheduleAtFixedRate: a poll blocking on SQLite's
        // busy_timeout=5000 must not queue up catch-up executions back-to-back once it returns.
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> poll(id, sessionId, userId, municipalityIbge, tick, terminalAttemptWaitTick,
                        lastSent, emitter, stopped, futureHolder, onDone),
                0, pollIntervalMs, TimeUnit.MILLISECONDS);
        futureHolder.set(future);
        if (stopped.get()) {
            // A zero-delay scheduler may run the first poll before this assignment. Completion is
            // already owned by onDone in that case; make the future cancellation visible after
            // the holder becomes available as well.
            future.cancel(false);
        }

        return emitter;
    }

    private void poll(
            String jobId, String sessionId, String userId, String municipalityIbge, AtomicInteger tick,
            AtomicInteger terminalAttemptWaitTick, AtomicReference<JobSnapshot> lastSent,
            SseEmitter emitter, AtomicBoolean stopped, AtomicReference<ScheduledFuture<?>> futureHolder,
            Runnable onDone) {
        if (stopped.get()) {
            // The connection was already completed by an earlier tick, before futureHolder had
            // been assigned (scheduleWithFixedDelay's first execution can start immediately, in a
            // race with the calling thread's own futureHolder.set(future)) — self-heals within
            // one extra tick instead of polling forever.
            ScheduledFuture<?> future = futureHolder.get();
            if (future != null) {
                future.cancel(false);
            }
            return;
        }
        try {
            if (tick.incrementAndGet() % reauthEveryNTicks == 0) {
                Instant now = clock.instant();
                boolean stillValid = sessionService.revalidate(sessionId, now)
                        && scopeResolver.hasPermission(userId, Permission.RUN_INDICATOR, municipalityIbge, null, null);
                if (!stillValid) {
                    // An expected outcome (§1.12.7 L541), not a server fault — a graceful close
                    // lets the client learn it lost access ("o cliente o confirma por GET",
                    // §1.10 L397), rather than surfacing as a dispatcher-level error.
                    onDone.run();
                    emitter.complete();
                    return;
                }
                // A heartbeat on every revalidation tick, not only on change: without it, a
                // client that silently disappears (closed tab, dropped connection) while the job
                // sits in an unchanging state is never discovered — send() never runs, so the
                // broken pipe never surfaces, and the scheduled task/limiter slot leak until the
                // 30-minute emitter timeout. ENG-44's own wording ("polling, SSE e heartbeat não
                // contam") anticipates exactly this heartbeat.
                emitter.send(SseEmitter.event().comment("keep-alive"));
            }

            Job current = jobRepository.findById(jobId).orElse(null);
            if (current == null) {
                onDone.run();
                emitter.completeWithError(new IllegalStateException("job " + jobId + " no longer exists"));
                return;
            }

            JobSnapshot snapshot = new JobSnapshot(current.state(), current.attempt(), current.lastProgressAt());
            boolean terminal = TERMINAL.contains(current.state());
            if (!terminal && snapshot.equals(lastSent.get())) {
                return;
            }
            RunResponse response = responseFactory.toResponse(current);
            boolean finalAttemptVisible = current.attempt() == 0
                    || response.attempts().stream().anyMatch(attempt -> attempt.attempt() == current.attempt());
            boolean terminalAttemptWaitExpired = terminal && !finalAttemptVisible
                    && terminalAttemptWaitTick.incrementAndGet() >= terminalAttemptWaitTicks;
            if (terminal && !finalAttemptVisible && !terminalAttemptWaitExpired) {
                return;
            }
            if (!snapshot.equals(lastSent.get())) {
                if (terminalAttemptWaitExpired) {
                    log.warn("closing terminal SSE stream for job {} without final attempt history", jobId);
                }
                emitter.send(SseEmitter.event().name("run").data(response, MediaType.APPLICATION_JSON));
                lastSent.set(snapshot);
            }
            if (terminal) {
                onDone.run();
                emitter.complete();
            }
        } catch (java.io.IOException e) {
            onDone.run();
            emitter.completeWithError(e);
        } catch (RuntimeException e) {
            onDone.run();
            emitter.completeWithError(e);
        }
    }

    private record JobSnapshot(JobState state, int attempt, Instant lastProgressAt) {
    }
}
