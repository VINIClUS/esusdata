package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.resultstore.ResultStagingArea;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
