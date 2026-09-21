package br.gov.observatorioaps.jobrunner.infrastructure.jdbc;

import br.gov.observatorioaps.jobrunner.domain.JobRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import br.gov.observatorioaps.jobrunner.application.JobRecovery;
import br.gov.observatorioaps.jobrunner.domain.EnqueueRequest;
import br.gov.observatorioaps.jobrunner.domain.Job;
import br.gov.observatorioaps.jobrunner.domain.JobState;
/**
 * All SQL against {@code jobs} and {@code job_attempts}. Every state-changing method is a
 * compare-and-swap: the {@code WHERE} clause always includes the expected {@code state} and, for
 * transitions owned by a running attempt, the exact {@code process_instance_id} and {@code
 * execution_generation} (§1.9.4: "qualquer alteração/publicação verifica processo, geração e
 * estado esperado"). A method returning {@code false}/0 rows means the CAS lost — the caller must
 * never treat that as success.
 */
public final class JdbcJobRepository implements JobRepository {

    private static final RowMapper<Job> MAPPER = (rs, rowNum) -> new Job(
            rs.getString("job_id"), rs.getString("run_id"), rs.getString("municipality_ibge"),
            rs.getString("indicator_pack"), rs.getString("rule_version"),
            rs.getString("reference_period"), JobState.valueOf(rs.getString("state")),
            rs.getInt("attempt"), rs.getInt("max_attempts"), rs.getString("process_instance_id"),
            rs.getLong("execution_generation"), instantOrNull(rs, "last_progress_at"),
            instantOrNull(rs, "next_attempt_at"), instantOrNull(rs, "created_at"),
            instantOrNull(rs, "started_at"), instantOrNull(rs, "finished_at"),
            rs.getString("failure_code"), rs.getString("failure_detail"),
            rs.getString("extraction_id"), rs.getString("idempotency_key"),
            rs.getString("source_id"), rs.getString("requested_scope_json"),
            rs.getString("idempotency_principal"), rs.getString("request_hash"),
            instantOrNull(rs, "idempotency_expires_at"), rs.getString("staging_id"),
            instantOrNull(rs, "cancel_requested_at"));

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Instant.parse(value);
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public JdbcJobRepository(JdbcTemplate jdbc, TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
    }

    public Job enqueue(EnqueueRequest request) {
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, execution_generation,
                    created_at, extraction_id, source_id, idempotency_principal, idempotency_key,
                    request_hash, idempotency_expires_at, requested_scope_json)
                VALUES (?,?,?,?,?, ?, 'QUEUED', 0, ?, 0, ?,?,?,?,?,?,?,?)
                """,
                request.jobId(), request.runId(), request.municipalityIbge(),
                request.indicatorPack(), request.ruleVersion(), request.referencePeriod(),
                request.maxAttempts(), request.createdAt().toString(), request.extractionId(),
                request.sourceId(), request.idempotencyPrincipal(), request.idempotencyKey(),
                request.requestHash(),
                request.idempotencyExpiresAt() == null ? null : request.idempotencyExpiresAt().toString(),
                request.requestedScopeJson());
        return findById(request.jobId()).orElseThrow();
    }

    public Optional<Job> findById(String jobId) {
        return jdbc.query("select * from jobs where job_id = ?", MAPPER, jobId)
                .stream().findFirst();
    }

    public Optional<Job> findByIdempotency(String principal, String idempotencyKey) {
        if (principal == null || idempotencyKey == null) return Optional.empty();
        return jdbc.query(
                "select * from jobs where idempotency_principal = ? and idempotency_key = ?",
                MAPPER, principal, idempotencyKey).stream().findFirst();
    }

    /**
     * Frees an expired idempotency key so a later request may reuse it (§1.9.5: "janela de
     * retenção dessa chave deve ser documentada"). The unique index has no notion of expiry — it
     * cannot, since SQLite partial-index predicates must be deterministic — so the resolver clears
     * the old association explicitly instead of relying on the constraint to allow the insert.
     * The job row itself, and its history, are untouched; only the idempotency linkage is dropped.
     */
    public void clearIdempotencyKey(String jobId) {
        jdbc.update("update jobs set idempotency_key = NULL where job_id = ?", jobId);
    }

    /**
     * Atomically claims the oldest runnable {@code QUEUED} job for this process, bumping its
     * generation inside a short transaction (§1.9.4: "a aquisição do job incrementa a geração em
     * transação curta"). Returns empty if there is nothing runnable right now.
     */
    public Optional<Job> acquireNext(String processInstanceId, Instant now) {
        return Optional.ofNullable(transactionTemplate.execute(status -> {
            List<String> candidates = jdbc.queryForList("""
                    select job_id from jobs
                     where state = 'QUEUED' and (next_attempt_at is null or next_attempt_at <= ?)
                     order by created_at limit 1
                    """, String.class, now.toString());
            if (candidates.isEmpty()) {
                return null;
            }
            String jobId = candidates.get(0);
            int updated = jdbc.update("""
                    UPDATE jobs SET state = 'RUNNING', process_instance_id = ?,
                        execution_generation = execution_generation + 1,
                        started_at = coalesce(started_at, ?), last_progress_at = ?,
                        attempt = attempt + 1
                     WHERE job_id = ? AND state = 'QUEUED'
                    """, processInstanceId, now.toString(), now.toString(), jobId);
            if (updated != 1) {
                return null; // lost the race — under the single-worker invariant this is only recovery overlap
            }
            return findById(jobId).orElseThrow();
        }));
    }

    public void markProgress(String jobId, String processInstanceId, long executionGeneration, Instant now) {
        jdbc.update("""
                UPDATE jobs SET last_progress_at = ?
                 WHERE job_id = ? AND state = 'RUNNING'
                   AND process_instance_id = ? AND execution_generation = ?
                """, now.toString(), jobId, processInstanceId, executionGeneration);
    }

    public boolean markStaged(String jobId, String processInstanceId, long executionGeneration, String stagingId) {
        return jdbc.update("""
                UPDATE jobs SET state = 'STAGED', staging_id = ?
                 WHERE job_id = ? AND state = 'RUNNING'
                   AND process_instance_id = ? AND execution_generation = ?
                """, stagingId, jobId, processInstanceId, executionGeneration) == 1;
    }

    /** Definitive failure — no more attempts. Terminal; never retried automatically. */
    public boolean markFailed(
            String jobId, String processInstanceId, long executionGeneration, JobState fromState,
            String failureCode, String failureDetail, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'FAILED', finished_at = ?, failure_code = ?, failure_detail = ?
                 WHERE job_id = ? AND state = ? AND process_instance_id = ? AND execution_generation = ?
                """, now.toString(), failureCode, failureDetail, jobId, fromState.name(),
                processInstanceId, executionGeneration) == 1;
    }

    /**
     * Live transient-failure retry: {@code RUNNING|STAGED -> QUEUED} with backoff — never visits
     * {@code FAILED} while attempts remain (§1.9.4). Callers clear any staged evidence themselves
     * before calling this (a retry always recomputes from scratch, never resumes a partial stage).
     */
    public boolean requeueForRetry(
            String jobId, String processInstanceId, long executionGeneration, JobState fromState,
            Instant nextAttemptAt, String failureCode, String failureDetail) {
        return jdbc.update("""
                UPDATE jobs SET state = 'QUEUED', process_instance_id = NULL, staging_id = NULL,
                    next_attempt_at = ?, failure_code = ?, failure_detail = ?
                 WHERE job_id = ? AND state = ?
                   AND process_instance_id = ? AND execution_generation = ?
                """, nextAttemptAt.toString(), failureCode, failureDetail, jobId, fromState.name(),
                processInstanceId, executionGeneration) == 1;
    }

    /** A pending cancel request against one observed running/staged attempt. */
    public boolean requestCancel(
            String jobId, String processInstanceId, long executionGeneration, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'CANCEL_REQUESTED', cancel_requested_at = ?
                 WHERE job_id = ? AND state IN ('RUNNING', 'STAGED')
                   AND process_instance_id = ? AND execution_generation = ?
                """, now.toString(), jobId, processInstanceId, executionGeneration) == 1;
    }

    /** Cancelling a job that never started — no attempt, no staging to neutralize. */
    public boolean cancelQueued(String jobId, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'CANCELLED', finished_at = ?,
                    failure_code = NULL, failure_detail = NULL
                WHERE job_id = ? AND state = 'QUEUED'
                """, now.toString(), jobId) == 1;
    }

    public boolean markCancelled(String jobId, String processInstanceId, long executionGeneration, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'CANCELLED', finished_at = ?,
                    failure_code = NULL, failure_detail = NULL
                WHERE job_id = ? AND state = 'CANCEL_REQUESTED'
                  AND process_instance_id = ? AND execution_generation = ?
                """, now.toString(), jobId, processInstanceId, executionGeneration) == 1;
    }

    /**
     * Publishes the terminal job transition and its successful attempt history in one transaction.
     * The publication transaction calls this method while it is already open, so the result,
     * staging row, job state, and attempt row commit or roll back together.
     */
    public boolean markSucceededAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration,
            String stagingId, Instant finishedAt) {
        return transitionAndRecordAttempt(
                jobId, processInstanceId, executionGeneration, JobState.STAGED, finishedAt,
                "SUCCEEDED", null, null,
                () -> jdbc.update("""
                        UPDATE jobs SET state = 'SUCCEEDED', staging_id = ?, finished_at = ?,
                            failure_code = NULL, failure_detail = NULL
                        WHERE job_id = ? AND state = 'STAGED'
                          AND process_instance_id = ? AND execution_generation = ?
                        """, stagingId, finishedAt.toString(), jobId, processInstanceId,
                        executionGeneration));
    }

    /** Failed terminal transition plus its attempt history, atomically. */
    public boolean markFailedAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, JobState fromState,
            String failureCode, String failureDetail, Instant finishedAt) {
        return transitionAndRecordAttempt(
                jobId, processInstanceId, executionGeneration, fromState, finishedAt,
                "FAILED_DEFINITIVE", failureCode, failureDetail,
                () -> jdbc.update("""
                        UPDATE jobs SET state = 'FAILED', finished_at = ?, failure_code = ?,
                            failure_detail = ?
                        WHERE job_id = ? AND state = ? AND process_instance_id = ?
                          AND execution_generation = ?
                        """, finishedAt.toString(), failureCode, failureDetail, jobId,
                        fromState.name(), processInstanceId, executionGeneration));
    }

    /** Cancellation terminal transition plus its attempt history, atomically. */
    public boolean markCancelledAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, Instant finishedAt) {
        return transitionAndRecordAttempt(
                jobId, processInstanceId, executionGeneration, JobState.CANCEL_REQUESTED, finishedAt,
                "CANCELLED", "CANCELLED", "cooperative cancellation completed",
                () -> jdbc.update("""
                        UPDATE jobs SET state = 'CANCELLED', finished_at = ?,
                            failure_code = NULL, failure_detail = NULL
                        WHERE job_id = ? AND state = 'CANCEL_REQUESTED'
                          AND process_instance_id = ? AND execution_generation = ?
                        """, finishedAt.toString(), jobId, processInstanceId, executionGeneration));
    }

    /** Retry transition plus its attempt history, atomically. */
    public boolean requeueForRetryAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, JobState fromState,
            Instant nextAttemptAt, String failureCode, String failureDetail, Instant finishedAt) {
        return transitionAndRecordAttempt(
                jobId, processInstanceId, executionGeneration, fromState, finishedAt,
                "FAILED_TRANSIENT", failureCode, failureDetail,
                () -> jdbc.update("""
                        UPDATE jobs SET state = 'QUEUED', process_instance_id = NULL, staging_id = NULL,
                            next_attempt_at = ?, failure_code = ?, failure_detail = ?
                        WHERE job_id = ? AND state = ? AND process_instance_id = ?
                          AND execution_generation = ?
                        """, nextAttemptAt.toString(), failureCode, failureDetail, jobId,
                        fromState.name(), processInstanceId, executionGeneration));
    }

    /**
     * Reads the owned attempt, applies a CAS transition, and records that attempt before the
     * transaction can commit. Reading before the update preserves the old process id for retry
     * transitions, which clear ownership on the jobs row.
     */
    private boolean transitionAndRecordAttempt(
            String jobId, String processInstanceId, long executionGeneration, JobState expectedState,
            Instant finishedAt, String outcome, String failureCode, String failureDetail,
            IntSupplier transition) {
        Boolean completed = transactionTemplate.execute(status -> {
            Job current = findById(jobId).orElse(null);
            if (current == null || current.state() != expectedState
                    || !processInstanceId.equals(current.processInstanceId())
                    || current.executionGeneration() != executionGeneration) {
                return false;
            }
            if (transition.getAsInt() != 1) {
                return false;
            }
            recordAttempt(jobId, current.attempt(), processInstanceId, executionGeneration,
                    current.startedAt() == null ? finishedAt : current.startedAt(), finishedAt,
                    outcome, failureCode, failureDetail);
            return true;
        });
        return Boolean.TRUE.equals(completed);
    }

    // --- recovery-only CAS transitions (JobRecovery is the only caller) -----------------------

    /** Jobs left {@code RUNNING}/{@code STAGED} by a process instance other than the current one. */
    public List<Job> findAbandoned(JobState state, String currentProcessInstanceId) {
        return jdbc.query(
                "select * from jobs where state = ? and (process_instance_id is null or process_instance_id <> ?)",
                MAPPER, state.name(), currentProcessInstanceId);
    }

    public boolean requeueAbandoned(String jobId, JobState fromState, Instant nextAttemptAt) {
        return jdbc.update("""
                UPDATE jobs SET state = 'QUEUED', execution_generation = execution_generation + 1,
                    process_instance_id = NULL, staging_id = NULL, next_attempt_at = ?
                 WHERE job_id = ? AND state = ?
                """, nextAttemptAt.toString(), jobId, fromState.name()) == 1;
    }

    public boolean failAbandoned(
            String jobId, JobState fromState, String failureCode, String failureDetail, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'FAILED', execution_generation = execution_generation + 1,
                    process_instance_id = NULL, staging_id = NULL, finished_at = ?,
                    failure_code = ?, failure_detail = ?
                 WHERE job_id = ? AND state = ?
                """, now.toString(), failureCode, failureDetail, jobId, fromState.name()) == 1;
    }

    public boolean cancelAbandoned(String jobId, Instant now) {
        return jdbc.update("""
                UPDATE jobs SET state = 'CANCELLED', process_instance_id = NULL, finished_at = ?,
                    failure_code = NULL, failure_detail = NULL
                WHERE job_id = ? AND state = 'CANCEL_REQUESTED'
                """, now.toString(), jobId) == 1;
    }

    // --- attempt history -------------------------------------------------------------------

    public void recordAttempt(
            String jobId, int attempt, String processInstanceId, long executionGeneration,
            Instant startedAt, Instant finishedAt, String outcome, String failureCode, String failureDetail) {
        jdbc.update("""
                INSERT INTO job_attempts (job_id, attempt, process_instance_id, execution_generation,
                    started_at, finished_at, outcome, failure_code, failure_detail)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, jobId, attempt, processInstanceId, executionGeneration, startedAt.toString(),
                finishedAt == null ? null : finishedAt.toString(), outcome, failureCode, failureDetail);
    }

    public List<AttemptRecord> findAttempts(String jobId) {
        return jdbc.query("select * from job_attempts where job_id = ? order by attempt",
                (rs, rowNum) -> new AttemptRecord(
                        rs.getString("job_id"), rs.getInt("attempt"),
                        rs.getString("process_instance_id"), rs.getLong("execution_generation"),
                        Instant.parse(rs.getString("started_at")),
                        instantOrNull(rs, "finished_at"), rs.getString("outcome"),
                        rs.getString("failure_code"), rs.getString("failure_detail")),
                jobId);
    }

}
