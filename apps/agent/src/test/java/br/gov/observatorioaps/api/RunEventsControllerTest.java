package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.identityaccess.ScopeResolver;
import br.gov.observatorioaps.identityaccess.SessionService;
import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunEventsControllerTest {

    @Test
    void unknownJobIsAuditedBeforeSseNotFound() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        RunEventsController controller = new RunEventsController(
                jobRepository, mock(RunResponseFactory.class), authorization, mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), mock(ScheduledExecutorService.class),
                mock(ScheduledExecutorService.class), Clock.fixed(now, ZoneOffset.UTC), 1000, 30);

        AuthenticatedSession session = sessionAt(now);
        assertThatThrownBy(() -> controller.events(session, "missing"))
                .isInstanceOf(ApiNotFoundException.class);

        verify(authorization).auditDenied(session, Permission.RUN_INDICATOR, "unknown");
    }

    @Test
    void terminalPollCancelsItsScheduledTaskEvenIfItRunsBeforeControllerReturns() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job job = new Job(
                "job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03",
                JobState.SUCCEEDED, 1, 3, "proc-1", 1, now, null, now, now, now,
                null, null, null, null, "src-1", null, null, null, null, null, null);

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        RunResponseFactory responseFactory = mock(RunResponseFactory.class);
        when(responseFactory.toResponse(job)).thenReturn(new RunResponse(
                "job-1", "run-1", "SUCCEEDED", 1, 3, "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", "src-1", null, now.toString(), now.toString(), now.toString(), now.toString(),
                null, null, null,
                List.of(new AttemptResponse(1, now.toString(), now.toString(), "SUCCEEDED", null, null))));

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ScheduledExecutorService reauthScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> reauthFuture = mock(ScheduledFuture.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return future;
                });
        when(reauthScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(1_000_000L), eq(1_000_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);
        when(reauthScheduler.scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);

        AuthenticatedSession session = new AuthenticatedSession(
                "session-1", "user-1", now, now, now.plusSeconds(3600), 1, null);
        RunEventsController controller = new RunEventsController(
                jobRepository, responseFactory, mock(ApiAuthorization.class), mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), scheduler, reauthScheduler,
                Clock.fixed(now, ZoneOffset.UTC), 1000, 1000);

        controller.events(session, "job-1");

        verify(future).cancel(false);
    }

    @Test
    void reauthorizationIsScheduledOnItsOwnCadence() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job job = new Job(
                "job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03",
                JobState.RUNNING, 1, 3, "proc-1", 1, now, null, now, null, now,
                null, null, null, null, "src-1", null, null, null, null, null, null);

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        ScheduledExecutorService pollScheduler = mock(ScheduledExecutorService.class);
        ScheduledExecutorService reauthScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> reauthFuture = mock(ScheduledFuture.class);
        when(pollScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> pollFuture);
        when(reauthScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);
        when(reauthScheduler.scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);

        Instant sessionNow = now;
        AuthenticatedSession session = new AuthenticatedSession(
                "session-1", "user-1", sessionNow, sessionNow, sessionNow.plusSeconds(3600), 1, null);
        RunEventsController controller = new RunEventsController(
                jobRepository, mock(RunResponseFactory.class), mock(ApiAuthorization.class), mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), pollScheduler, reauthScheduler,
                Clock.fixed(now, ZoneOffset.UTC), 1000, 30);

        controller.events(session, "job-1");

        verify(reauthScheduler).scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void reauthorizationIntervalIsCappedAtThirtySeconds() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job job = runningJob(now);
        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        ScheduledExecutorService pollScheduler = mock(ScheduledExecutorService.class);
        ScheduledExecutorService reauthScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> reauthFuture = mock(ScheduledFuture.class);
        when(pollScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> pollFuture);
        when(reauthScheduler.scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);

        RunEventsController controller = new RunEventsController(
                jobRepository, mock(RunResponseFactory.class), mock(ApiAuthorization.class), mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), pollScheduler, reauthScheduler,
                Clock.fixed(now, ZoneOffset.UTC), 1000, 60);

        controller.events(sessionAt(now), "job-1");

        verify(reauthScheduler).scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void revokedScopeIsAuditedBeforeSseTeardown() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job job = runningJob(now);
        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        ScheduledExecutorService pollScheduler = mock(ScheduledExecutorService.class);
        ScheduledExecutorService reauthScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> reauthFuture = mock(ScheduledFuture.class);
        when(pollScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> pollFuture);
        AtomicReference<Runnable> reauthTask = new AtomicReference<>();
        when(reauthScheduler.scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
                    reauthTask.set(invocation.getArgument(0));
                    return reauthFuture;
                });
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        ScopeResolver scopeResolver = mock(ScopeResolver.class);
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.revalidate("session-1", now)).thenReturn(true);
        when(scopeResolver.hasPermission("user-1", Permission.RUN_INDICATOR, "3541307", null, null))
                .thenReturn(false);

        RunEventsController controller = new RunEventsController(
                jobRepository, mock(RunResponseFactory.class), authorization, scopeResolver, sessionService,
                new SseConnectionLimiter(), pollScheduler, reauthScheduler, Clock.fixed(now, ZoneOffset.UTC),
                1000, 30);

        controller.events(sessionAt(now), "job-1");
        reauthTask.get().run();

        verify(authorization).auditDenied(any(AuthenticatedSession.class), eq(Permission.RUN_INDICATOR),
                eq("3541307"));
    }

    @Test
    void terminalAttemptGraceStartsAtFirstObservationEvenWithLongPollInterval() {
        Instant firstObservation = Instant.parse("2026-09-20T12:00:00Z");
        Job job = new Job(
                "job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03",
                JobState.SUCCEEDED, 1, 3, "proc-1", 1, firstObservation, null, firstObservation,
                firstObservation, firstObservation, null, null, null, null, "src-1", null, null, null, null, null, null);
        RunResponse response = new RunResponse(
                "job-1", "run-1", "SUCCEEDED", 1, 3, "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", "src-1", null, firstObservation.toString(), firstObservation.toString(),
                firstObservation.toString(), firstObservation.toString(), null, null, null, List.of());
        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        RunResponseFactory responseFactory = mock(RunResponseFactory.class);
        when(responseFactory.toResponse(job)).thenReturn(response);
        ScheduledExecutorService pollScheduler = mock(ScheduledExecutorService.class);
        ScheduledExecutorService reauthScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> pollFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> reauthFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> pollTask = new AtomicReference<>();
        when(pollScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(5000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    pollTask.set(invocation.getArgument(0));
                    return pollFuture;
                });
        when(reauthScheduler.scheduleAtFixedRate(any(Runnable.class), eq(30_000L), eq(30_000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> reauthFuture);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(firstObservation, firstObservation.plusSeconds(5));

        RunEventsController controller = new RunEventsController(
                jobRepository, responseFactory, mock(ApiAuthorization.class), mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), pollScheduler, reauthScheduler,
                clock, 5000, 30);

        controller.events(sessionAt(firstObservation), "job-1");
        pollTask.get().run();
        verify(pollFuture, never()).cancel(false);

        pollTask.get().run();
        verify(pollFuture).cancel(false);
    }

    private static AuthenticatedSession sessionAt(Instant now) {
        return new AuthenticatedSession(
                "session-1", "user-1", now, now, now.plusSeconds(3600), 1, null);
    }

    private static Job runningJob(Instant now) {
        return new Job(
                "job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03",
                JobState.RUNNING, 1, 3, "proc-1", 1, now, null, now, null, now,
                null, null, null, null, "src-1", null, null, null, null, null, null);
    }
}
