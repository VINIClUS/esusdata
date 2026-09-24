package esusdata.run.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.result.model.ExtractionManifestRepository;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.Job;
import esusdata.run.job.JobNotCancellableException;
import esusdata.run.job.JobRepository;
import esusdata.run.job.JobState;
import esusdata.run.worker.CancellationRegistry;
import esusdata.run.worker.IdempotencyResolver;
import esusdata.source.SourceRepository;
import esusdata.web.ApiNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunControllerCancellationTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String MUNICIPALITY = "3541307";

    @Test
    void aQueuedCancelCasLossReloadsAndCancelsTheNowRunningJob() {
        JobRepository jobRepository = mock(JobRepository.class);
        CancellationRegistry cancellationRegistry = new CancellationRegistry();
        cancellationRegistry.register("job-1");
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        doNothing().when(authorization).requireObjectScope(session, Permission.RUN_INDICATOR, MUNICIPALITY);

        Job queued = job(JobState.QUEUED);
        Job running = job(JobState.RUNNING);
        Job requested = job(JobState.CANCEL_REQUESTED);
        when(jobRepository.findById("job-1"))
                .thenReturn(Optional.of(queued))
                .thenReturn(Optional.of(running))
                .thenReturn(Optional.of(requested));
        when(jobRepository.cancelQueued(eq("job-1"), any(Instant.class))).thenReturn(false);
        when(jobRepository.requestCancel(eq("job-1"), eq("proc-1"), eq(1L), any(Instant.class)))
                .thenReturn(true);
        when(jobRepository.findAttempts("job-1")).thenReturn(List.of());

        RunController controller = new RunController(
                jobRepository,
                mock(IdempotencyResolver.class),
                cancellationRegistry,
                new RunResponseFactory(jobRepository, mock(ResultRepository.class)),
                mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunResponse response = controller.cancel(session, "job-1");

        verify(jobRepository).requestCancel("job-1", "proc-1", 1L, NOW);
        assertThat(response.state()).isEqualTo(JobState.CANCEL_REQUESTED.name());
        assertThat(cancellationRegistry.find("job-1").orElseThrow().isCancelRequested())
                .isTrue();
    }

    @Test
    void anAlreadyCancelRequestedJobIsReloadedBeforeReturningItsState() {
        JobRepository jobRepository = mock(JobRepository.class);
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        doNothing().when(authorization).requireObjectScope(session, Permission.RUN_INDICATOR, MUNICIPALITY);

        when(jobRepository.findById("job-1"))
                .thenReturn(Optional.of(job(JobState.CANCEL_REQUESTED)))
                .thenReturn(Optional.of(job(JobState.SUCCEEDED)))
                .thenReturn(Optional.of(job(JobState.SUCCEEDED)));

        RunController controller = new RunController(
                jobRepository,
                mock(IdempotencyResolver.class),
                new CancellationRegistry(),
                new RunResponseFactory(jobRepository, mock(ResultRepository.class)),
                mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> controller.cancel(session, "job-1")).isInstanceOf(JobNotCancellableException.class);
    }

    @Test
    void cancellationKeepsRetryingWhenOwnershipChangesTwiceDuringTheRequest() {
        JobRepository jobRepository = mock(JobRepository.class);
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        doNothing().when(authorization).requireObjectScope(session, Permission.RUN_INDICATOR, MUNICIPALITY);

        when(jobRepository.findById("job-1"))
                .thenReturn(Optional.of(job(JobState.RUNNING, "proc-1", 1)))
                .thenReturn(Optional.of(job(JobState.QUEUED, null, 2)))
                .thenReturn(Optional.of(job(JobState.RUNNING, "proc-2", 3)))
                .thenReturn(Optional.of(job(JobState.CANCEL_REQUESTED, "proc-2", 3)));
        when(jobRepository.requestCancel(eq("job-1"), eq("proc-1"), eq(1L), any(Instant.class)))
                .thenReturn(false);
        when(jobRepository.cancelQueued(eq("job-1"), any(Instant.class))).thenReturn(false);
        when(jobRepository.requestCancel(eq("job-1"), eq("proc-2"), eq(3L), any(Instant.class)))
                .thenReturn(true);
        when(jobRepository.findAttempts("job-1")).thenReturn(List.of());

        RunController controller = new RunController(
                jobRepository,
                mock(IdempotencyResolver.class),
                new CancellationRegistry(),
                new RunResponseFactory(jobRepository, mock(ResultRepository.class)),
                mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunResponse response = controller.cancel(session, "job-1");

        verify(jobRepository).requestCancel("job-1", "proc-2", 3L, NOW);
        assertThat(response.state()).isEqualTo(JobState.CANCEL_REQUESTED.name());
    }

    @Test
    void anUnknownJobIsAuditedBeforeReturningNotFound() {
        JobRepository jobRepository = mock(JobRepository.class);
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        RunController controller = new RunController(
                jobRepository,
                mock(IdempotencyResolver.class),
                new CancellationRegistry(),
                new RunResponseFactory(jobRepository, mock(ResultRepository.class)),
                mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class),
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> controller.get(session, "missing")).isInstanceOf(ApiNotFoundException.class);
        verify(authorization).auditDenied(session, Permission.RUN_INDICATOR, "unknown");
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession("session-1", "user-1", NOW, NOW, NOW.plusSeconds(3600), 1, null);
    }

    private static Job job(JobState state) {
        return job(state, "proc-1", 1);
    }

    private static Job job(JobState state, String processInstanceId, long executionGeneration) {
        return new Job(
                "job-1",
                "run-1",
                MUNICIPALITY,
                "c1-mais-acesso",
                "c1-mais-acesso@0.1.0",
                "2026-03",
                state,
                state == JobState.QUEUED ? 0 : 1,
                3,
                processInstanceId,
                executionGeneration,
                null,
                null,
                NOW,
                state == JobState.QUEUED ? null : NOW,
                null,
                null,
                null,
                null,
                null,
                "src-1",
                null,
                "user-1",
                "hash-1",
                null,
                null,
                null);
    }
}
