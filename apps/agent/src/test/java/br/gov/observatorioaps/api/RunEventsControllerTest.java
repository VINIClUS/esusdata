package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunEventsControllerTest {

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
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return future;
                });

        AuthenticatedSession session = new AuthenticatedSession(
                "session-1", "user-1", now, now, now.plusSeconds(3600), 1, null);
        RunEventsController controller = new RunEventsController(
                jobRepository, responseFactory, mock(ApiAuthorization.class), mock(ScopeResolver.class),
                mock(SessionService.class), new SseConnectionLimiter(), scheduler,
                Clock.fixed(now, ZoneOffset.UTC), 1000, 1000);

        controller.events(session, "job-1");

        verify(future).cancel(false);
    }
}
