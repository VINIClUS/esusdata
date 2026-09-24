package esusdata.run.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * All SQL against {@code jobs} and {@code job_attempts}. Every state-changing method is a
 * compare-and-swap: the {@code WHERE} clause always includes the expected {@code state} and, for
 * transitions owned by a running attempt, the exact {@code process_instance_id} and {@code
 * execution_generation} (§1.9.4: "qualquer alteração/publicação verifica processo, geração e
 * estado esperado"). A method returning {@code false}/0 rows means the CAS lost — the caller must
 * never treat that as success.
 */
public interface JobRepository {
    record AttemptRecord(
            String jobId,
            int attempt,
            String processInstanceId,
            long executionGeneration,
            Instant startedAt,
            Instant finishedAt,
            String outcome,
            String failureCode,
            String failureDetail) {}

    Job enqueue(EnqueueRequest request);

    Optional<Job> findById(String jobId);

    /** The municipality's most recently created jobs, newest first. */
    List<Job> findRecent(String municipalityIbge, int limit);

    Optional<Job> findByIdempotency(String principal, String idempotencyKey);

    /**
     * Frees an expired idempotency key so a later request may reuse it (§1.9.5: "janela de
     * retenção dessa chave deve ser documentada"). The unique index has no notion of expiry — it
     * cannot, since SQLite partial-index predicates must be deterministic — so the resolver clears
     * the old association explicitly instead of relying on the constraint to allow the insert.
     * The job row itself, and its history, are untouched; only the idempotency linkage is dropped.
     */
    void clearIdempotencyKey(String jobId);

    /**
     * Atomically claims the oldest runnable {@code QUEUED} job for this process, bumping its
     * generation inside a short transaction (§1.9.4: "a aquisição do job incrementa a geração em
     * transação curta"). Returns empty if there is nothing runnable right now.
     */
    Optional<Job> acquireNext(String processInstanceId, Instant now);

    void markProgress(String jobId, String processInstanceId, long executionGeneration, Instant now);

    boolean markStaged(String jobId, String processInstanceId, long executionGeneration, String stagingId);

    /** Definitive failure — no more attempts. Terminal; never retried automatically. */
    boolean markFailed(
            String jobId,
            String processInstanceId,
            long executionGeneration,
            JobState fromState,
            String failureCode,
            String failureDetail,
            Instant now);

    /**
     * Live transient-failure retry: {@code RUNNING|STAGED -> QUEUED} with backoff — never visits
     * {@code FAILED} while attempts remain (§1.9.4). Callers clear any staged evidence themselves
     * before calling this (a retry always recomputes from scratch, never resumes a partial stage).
     */
    boolean requeueForRetry(
            String jobId,
            String processInstanceId,
            long executionGeneration,
            JobState fromState,
            Instant nextAttemptAt,
            String failureCode,
            String failureDetail);

    /** A pending cancel request against one observed running/staged attempt. */
    boolean requestCancel(String jobId, String processInstanceId, long executionGeneration, Instant now);

    /** Cancelling a job that never started — no attempt, no staging to neutralize. */
    boolean cancelQueued(String jobId, Instant now);

    boolean markCancelled(String jobId, String processInstanceId, long executionGeneration, Instant now);

    /**
     * Publishes the terminal job transition and its successful attempt history in one transaction.
     * The publication transaction calls this method while it is already open, so the result,
     * staging row, job state, and attempt row commit or roll back together.
     */
    boolean markSucceededAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, String stagingId, Instant finishedAt);

    /** Failed terminal transition plus its attempt history, atomically. */
    boolean markFailedAndRecordAttempt(
            String jobId,
            String processInstanceId,
            long executionGeneration,
            JobState fromState,
            String failureCode,
            String failureDetail,
            Instant finishedAt);

    /** Cancellation terminal transition plus its attempt history, atomically. */
    boolean markCancelledAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, Instant finishedAt);

    /** Retry transition plus its attempt history, atomically. */
    boolean requeueForRetryAndRecordAttempt(
            String jobId,
            String processInstanceId,
            long executionGeneration,
            JobState fromState,
            Instant nextAttemptAt,
            String failureCode,
            String failureDetail,
            Instant finishedAt);

    // --- recovery-only CAS transitions (JobRecovery is the only caller) -----------------------

    /** Jobs left {@code RUNNING}/{@code STAGED} by a process instance other than the current one. */
    List<Job> findAbandoned(JobState state, String currentProcessInstanceId);

    boolean requeueAbandoned(String jobId, JobState fromState, Instant nextAttemptAt);

    boolean failAbandoned(String jobId, JobState fromState, String failureCode, String failureDetail, Instant now);

    boolean cancelAbandoned(String jobId, Instant now);

    void recordAttempt(
            String jobId,
            int attempt,
            String processInstanceId,
            long executionGeneration,
            Instant startedAt,
            Instant finishedAt,
            String outcome,
            String failureCode,
            String failureDetail);

    List<AttemptRecord> findAttempts(String jobId);
}
