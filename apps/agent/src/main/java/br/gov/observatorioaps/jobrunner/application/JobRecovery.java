package br.gov.observatorioaps.jobrunner.application;

import br.gov.observatorioaps.resultstore.domain.ResultStagingArea;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import br.gov.observatorioaps.jobrunner.domain.Job;
import br.gov.observatorioaps.jobrunner.domain.JobState;
import br.gov.observatorioaps.jobrunner.domain.RetryPolicy;
import br.gov.observatorioaps.jobrunner.domain.JobRepository;
/**
 * Boot-time reconciliation (§1.9.4, ENG-06, ENG-21, ENG-51). Runs once per process start, after
 * the {@code ProcessLock} and Flyway migration, before the worker accepts any job — enforced by
 * bean wiring order in {@code JobRunnerConfig}, not by convention.
 *
 * <p>Never republishes anything and never resumes a query — a {@code RUNNING} job found here is
 * definitely not this process's own work (its {@code process_instance_id} predates this boot's
 * fresh id), so it is either requeued for another attempt or failed, and any staged evidence is
 * neutralized. "Não iniciar outro worker enquanto o anterior ainda puder ler a fonte ou produzir
 * efeitos" — this class does not take over a live PEC session, it only blocks new ones on that
 * source for a bounded cooldown.
 */
public final class JobRecovery {

    private final JobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final ResultStagingArea stagingArea;
    private final AcquisitionGuard acquisitionGuard;
    private final RetryPolicy retryPolicy;
    private final Clock clock;
    private final Duration liveAcquisitionCooldownMargin;

    public JobRecovery(
            JobRepository jobRepository,
            TransactionTemplate transactionTemplate,
            ResultStagingArea stagingArea,
            AcquisitionGuard acquisitionGuard,
            RetryPolicy retryPolicy,
            Clock clock,
            Duration liveAcquisitionCooldownMargin) {
        this.jobRepository = jobRepository;
        this.transactionTemplate = transactionTemplate;
        this.stagingArea = stagingArea;
        this.acquisitionGuard = acquisitionGuard;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.liveAcquisitionCooldownMargin = liveAcquisitionCooldownMargin;
    }

    public record RecoveryReport(int requeued, int failed, int cancelled) {
    }

    public RecoveryReport reconcile(String newProcessInstanceId) {
        int requeued = 0;
        int failed = 0;
        int cancelled = 0;

        for (JobState state : List.of(JobState.RUNNING, JobState.STAGED)) {
            for (Job job : jobRepository.findAbandoned(state, newProcessInstanceId)) {
                if (recoverRunningOrStaged(job)) {
                    requeued++;
                } else {
                    failed++;
                }
            }
        }
        for (Job job : jobRepository.findAbandoned(JobState.CANCEL_REQUESTED, newProcessInstanceId)) {
            recoverCancelRequested(job);
            cancelled++;
        }
        return new RecoveryReport(requeued, failed, cancelled);
    }

    /** Returns {@code true} if the job was requeued, {@code false} if it was failed instead. */
    private boolean recoverRunningOrStaged(Job job) {
        Boolean requeued = transactionTemplate.execute(status -> {
            if (job.stagingId() != null) {
                stagingArea.neutralize(job.stagingId());
            }
            boolean retriable = retryPolicy.canRetry(job.attempt(), job.maxAttempts());
            Instant now = clock.instant();

            Instant blockedUntil = null;
            if (!job.isImmutableExtract() && job.state() == JobState.RUNNING) {
                // A RUNNING LIVE_READ_ONLY job may have had a live PEC session open; an
                // IMMUTABLE_EXTRACT job never opens one (it only reads a finalized extract file),
                // and a STAGED job's acquisition was already closed before staging began — neither
                // needs the guard.
                blockedUntil = now.plus(liveAcquisitionCooldownMargin);
                acquisitionGuard.block(job.sourceId(), blockedUntil,
                        "recovered abandoned RUNNING job " + job.jobId());
            }

            boolean transitioned;
            if (retriable) {
                Instant nextAttemptAt = retryPolicy.nextAttemptAt(now, job.attempt());
                if (blockedUntil != null && blockedUntil.isAfter(nextAttemptAt)) {
                    // Ordinary retry backoff starts far shorter than the cooldown just written
                    // above — without this, the requeued attempt would fire while still blocked,
                    // hit AcquisitionGuard, and burn retry budget on a wait condition instead of a
                    // real failure (the same class of bug JobWorker.handleFailure guards against
                    // for a live failure discovered at runtime, not at boot).
                    nextAttemptAt = blockedUntil;
                }
                transitioned = jobRepository.requeueAbandoned(job.jobId(), job.state(), nextAttemptAt);
            } else {
                transitioned = jobRepository.failAbandoned(job.jobId(), job.state(),
                        "RECOVERED_ABANDONED", "attempts exhausted after process restart", now);
            }
            if (!transitioned) {
                status.setRollbackOnly();
                throw new IllegalStateException("recovery CAS failed for job " + job.jobId());
            }
            jobRepository.recordAttempt(job.jobId(), job.attempt(), job.processInstanceId(),
                    job.executionGeneration(), job.startedAt() == null ? now : job.startedAt(), now,
                    "ABANDONED", "RECOVERED_ABANDONED",
                    "process restarted while job was " + job.state());
            return retriable;
        });
        return Boolean.TRUE.equals(requeued);
    }

    private void recoverCancelRequested(Job job) {
        transactionTemplate.executeWithoutResult(status -> {
            if (job.stagingId() != null) {
                stagingArea.neutralize(job.stagingId());
            }
            Instant now = clock.instant();
            if (!job.isImmutableExtract()) {
                // A CANCEL_REQUESTED LIVE_READ_ONLY job may still have been streaming from
                // PostgreSQL when this process died — cancellation is cooperative and best-effort
                // (CancellationToken's own contract), so an abandoned CANCEL_REQUESTED job is no
                // more provably closed than an abandoned RUNNING one. Same cooldown, same reason.
                acquisitionGuard.block(job.sourceId(), now.plus(liveAcquisitionCooldownMargin),
                        "recovered abandoned CANCEL_REQUESTED job " + job.jobId());
            }
            boolean transitioned = jobRepository.cancelAbandoned(job.jobId(), now);
            if (!transitioned) {
                status.setRollbackOnly();
                throw new IllegalStateException("recovery CAS failed for job " + job.jobId());
            }
            jobRepository.recordAttempt(job.jobId(), job.attempt(), job.processInstanceId(),
                    job.executionGeneration(), job.startedAt() == null ? now : job.startedAt(), now,
                    "ABANDONED", "RECOVERED_ABANDONED",
                    "process restarted with a pending cancel request");
        });
    }
}
