package br.gov.observatorioaps.execution.application;

import br.gov.observatorioaps.results.domain.ResultStagingArea;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import br.gov.observatorioaps.execution.domain.job.Job;
import br.gov.observatorioaps.execution.domain.job.JobState;
import br.gov.observatorioaps.execution.domain.job.RetryPolicy;
import br.gov.observatorioaps.execution.domain.job.SourceAcquisitionBlockedException;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
/**
 * §1.9.4: "um worker de cálculo ativo por instalação" — the worker thread must survive an
 * infrastructure hiccup, not die with it. {@code acquireNext} is a single CAS query; under
 * concurrent acquisition attempts its only realistic failure mode is raw SQLite write contention
 * (the same {@code SQLITE_BUSY} that {@link SingleWorkerConcurrencyTest} treats as retryable for
 * every other caller).
 */
class JobWorkerTest {

    @Test
    void runOnceSurvivesATransientAcquisitionFailureInsteadOfKillingTheWorkerThread() {
        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.acquireNext(anyString(), any(Instant.class)))
                .thenThrow(new TransientDataAccessResourceException("SQLITE_BUSY: database is locked"));

        JobWorker worker = new JobWorker(
                jobRepository,
                mock(IndicatorRunExecutor.class),
                mock(ResultStagingArea.class),
                new CancellationRegistry(),
                RetryPolicy.defaultPolicy(),
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC),
                "proc-1",
                Duration.ofMillis(1));

        // Before the fix, this exception would escape runOnce() and, in runLoop(), terminate the
        // worker thread while `running` stayed true — the process would look alive but never
        // process another job until restart.
        assertThatCode(worker::runOnce).doesNotThrowAnyException();
        assertThat(worker.runOnce()).isFalse();
    }

    /**
     * A deeper door to the same failure: {@code processJob}'s own recovery handling
     * ({@code handleFailure}) makes its own unguarded DB call ({@code findById}) to resolve what
     * to do after a job fails. If that call itself hits transient contention — plausible exactly
     * during a failure storm, when several such calls are writing at once — it must not kill the
     * worker thread either, even though the failure this time didn't originate in
     * {@code acquireNext} at all.
     */
    @Test
    void runOnceSurvivesATransientFailureInsideItsOwnFailureHandling() throws Exception {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job job = new Job("job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", JobState.RUNNING, 0, 3, "proc-1", 1, null, null, now, now, null,
                null, null, "ext-1", null, "src-1", null, null, null, null, null, null);

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.acquireNext(anyString(), any(Instant.class))).thenReturn(Optional.of(job));
        when(jobRepository.findById("job-1"))
                .thenThrow(new TransientDataAccessResourceException("SQLITE_BUSY: database is locked"));

        IndicatorRunExecutor executor = mock(IndicatorRunExecutor.class);
        when(executor.runFromExtract(any(), any())).thenThrow(new IllegalStateException("boom"));

        JobWorker worker = new JobWorker(
                jobRepository,
                executor,
                mock(ResultStagingArea.class),
                new CancellationRegistry(),
                RetryPolicy.defaultPolicy(),
                Clock.fixed(now, ZoneOffset.UTC),
                "proc-1",
                Duration.ofMillis(1));

        assertThatCode(worker::runOnce).doesNotThrowAnyException();
    }

    /**
     * PR review regression: ordinary retry backoff starts at seconds, but the ENG-51 cooldown
     * ({@link AcquisitionGuard}) can run to tens of seconds — without honouring
     * {@link SourceAcquisitionBlockedException#blockedUntil()}, the retry would fire while still
     * blocked, re-trip the guard, and get classified away as a hard failure before the job's
     * retry budget was ever exhausted.
     */
    @Test
    void handleFailureSchedulesTheRetryNoEarlierThanTheAcquisitionCooldown() throws Exception {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Instant blockedUntil = now.plusSeconds(65); // outlives the default policy's first backoff
        Job job = new Job("job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", JobState.RUNNING, 0, 3, "proc-1", 1, null, null, now, now, null,
                null, null, null, null, "src-1", null, null, null, null, null, null);

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.acquireNext(anyString(), any(Instant.class))).thenReturn(Optional.of(job));
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.requeueForRetryAndRecordAttempt(
                any(), any(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        IndicatorRunExecutor executor = mock(IndicatorRunExecutor.class);
        when(executor.runLive(any(), any())).thenThrow(new SourceAcquisitionBlockedException(
                "source src-1 is on cooldown until " + blockedUntil, blockedUntil));

        JobWorker worker = new JobWorker(
                jobRepository, executor, mock(ResultStagingArea.class), new CancellationRegistry(),
                RetryPolicy.defaultPolicy(), Clock.fixed(now, ZoneOffset.UTC), "proc-1", Duration.ofMillis(1));

        worker.runOnce();

        ArgumentCaptor<Instant> nextAttemptAt = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).requeueForRetryAndRecordAttempt(
                eq("job-1"), eq("proc-1"), eq(1L), eq(JobState.RUNNING), nextAttemptAt.capture(),
                eq("SOURCE_ACQUISITION_BLOCKED"), any(), eq(now));
        assertThat(nextAttemptAt.getValue()).isEqualTo(blockedUntil);
        verify(jobRepository, never()).markFailedAndRecordAttempt(
                any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void rechecksPersistedCancellationImmediatelyAfterRegisteringTheToken() throws Exception {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        Job acquired = new Job("job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", JobState.RUNNING, 1, 3, "proc-1", 1, null, null, now, now, null,
                null, null, "ext-1", null, "src-1", null, "user-1", null, null, null, null);
        Job persistedCancellation = new Job(
                "job-1", "run-1", "3541307", "c1-mais-acesso", "c1-mais-acesso@0.1.0",
                "2026-03", JobState.CANCEL_REQUESTED, 1, 3, "proc-1", 1, null, null, now, now, null,
                null, null, "ext-1", null, "src-1", null, "user-1", null, null, null, now);

        JobRepository jobRepository = mock(JobRepository.class);
        when(jobRepository.acquireNext(anyString(), any(Instant.class))).thenReturn(Optional.of(acquired));
        when(jobRepository.findById("job-1"))
                .thenReturn(Optional.of(persistedCancellation), Optional.of(persistedCancellation));
        when(jobRepository.markCancelledAndRecordAttempt("job-1", "proc-1", 1, now)).thenReturn(true);

        IndicatorRunExecutor executor = mock(IndicatorRunExecutor.class);
        JobWorker worker = new JobWorker(
                jobRepository, executor, mock(ResultStagingArea.class), new CancellationRegistry(),
                RetryPolicy.defaultPolicy(), Clock.fixed(now, ZoneOffset.UTC), "proc-1", Duration.ofMillis(1));

        worker.runOnce();

        verify(executor, never()).runFromExtract(any(), any());
        verify(jobRepository).markCancelledAndRecordAttempt("job-1", "proc-1", 1, now);
    }
}
