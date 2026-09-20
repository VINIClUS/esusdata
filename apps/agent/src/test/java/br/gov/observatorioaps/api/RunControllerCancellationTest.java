package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.jobrunner.CancellationRegistry;
import br.gov.observatorioaps.jobrunner.IdempotencyResolver;
import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.ResultRepository;
import br.gov.observatorioaps.resultstore.SourceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .thenReturn(Optional.of(queued), Optional.of(running), Optional.of(requested));
        when(jobRepository.cancelQueued(eq("job-1"), any(Instant.class))).thenReturn(false);
        when(jobRepository.requestCancel(eq("job-1"), eq("proc-1"), eq(1L), any(Instant.class)))
                .thenReturn(true);
        when(jobRepository.findAttempts("job-1")).thenReturn(List.of());

        RunController controller = new RunController(
                jobRepository, mock(IdempotencyResolver.class), cancellationRegistry,
                mock(ResultRepository.class), mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class), authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunResponse response = controller.cancel(session, "job-1");

        verify(jobRepository).requestCancel("job-1", "proc-1", 1L, NOW);
        assertThat(response.state()).isEqualTo(JobState.CANCEL_REQUESTED.name());
        assertThat(cancellationRegistry.find("job-1").orElseThrow().isCancelRequested()).isTrue();
    }

    @Test
    void anAlreadyCancelRequestedJobIsReloadedBeforeReturningItsState() {
        JobRepository jobRepository = mock(JobRepository.class);
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        doNothing().when(authorization).requireObjectScope(session, Permission.RUN_INDICATOR, MUNICIPALITY);

        when(jobRepository.findById("job-1"))
                .thenReturn(Optional.of(job(JobState.CANCEL_REQUESTED)),
                        Optional.of(job(JobState.SUCCEEDED)), Optional.of(job(JobState.SUCCEEDED)));

        RunController controller = new RunController(
                jobRepository, mock(IdempotencyResolver.class), new CancellationRegistry(),
                mock(ResultRepository.class), mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class), authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> controller.cancel(session, "job-1"))
                .isInstanceOf(JobNotCancellableException.class);
    }

    @Test
    void anUnknownJobIsAuditedBeforeReturningNotFound() {
        JobRepository jobRepository = mock(JobRepository.class);
        ApiAuthorization authorization = mock(ApiAuthorization.class);
        AuthenticatedSession session = session();
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        RunController controller = new RunController(
                jobRepository, mock(IdempotencyResolver.class), new CancellationRegistry(),
                mock(ResultRepository.class), mock(SourceRepository.class),
                mock(ExtractionManifestRepository.class), authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> controller.get(session, "missing"))
                .isInstanceOf(ApiNotFoundException.class);
        verify(authorization).auditDenied(session, Permission.RUN_INDICATOR, "unknown");
    }

    private AuthenticatedSession session() {
        return new AuthenticatedSession(
                "session-1", "user-1", NOW, NOW, NOW.plusSeconds(3600), 1, null);
    }

    private Job job(JobState state) {
        return new Job(
                "job-1", "run-1", MUNICIPALITY, "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", state, state == JobState.QUEUED ? 0 : 1, 3, "proc-1", 1,
                null, null, NOW, state == JobState.QUEUED ? null : NOW, null,
                null, null, null, null, "src-1", null, "user-1", "hash-1", null, null, null);
    }
}
