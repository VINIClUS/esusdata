package esusdata.run.worker;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Locale;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobRequestConflictException;
import esusdata.run.job.JobRepository;
/**
 * ENG-24: "mesmo pedido/chave reutiliza job somente com autorização atual; payload/município/
 * escopo diferente conflita; nova aquisição retificada gera nova entrada/run." Separates request
 * idempotency (this class) from data identity (§1.9.5) — a request without a key always creates a
 * new job.
 */
public final class IdempotencyResolver {

    private final JobRepository jobRepository;
    private final Clock clock;

    public IdempotencyResolver(JobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    public Job resolve(EnqueueRequest request) {
        if (request.idempotencyPrincipal() == null || request.idempotencyKey() == null) {
            return jobRepository.enqueue(request);
        }

        var existing = jobRepository.findByIdempotency(
                request.idempotencyPrincipal(), request.idempotencyKey());
        if (existing.isPresent()) {
            Job job = existing.get();
            boolean expired = job.idempotencyExpiresAt() != null
                    && job.idempotencyExpiresAt().isBefore(clock.instant());
            if (!expired) {
                if (!request.requestHash().equals(job.requestHash())) {
                    throw new JobRequestConflictException(
                            "idempotency key " + request.idempotencyKey()
                                    + " was already used with a different request payload");
                }
                return job; // identical repetition — same job, current authorization already
                            // re-checked by whatever authorizes the caller before reaching here
            }
            // Expired: free the key so the unique index (which has no notion of expiry) accepts
            // a fresh job under the same (principal, key) pair.
            jobRepository.clearIdempotencyKey(job.jobId());
        }

        try {
            return jobRepository.enqueue(request);
        } catch (DataAccessException raced) {
            if (!isIdempotencyKeyUniqueViolation(raced)) {
                throw raced;
            }
            // Lost a race against a concurrent submission under the same key — both requests
            // passed the findByIdempotency check above before either inserted. Reload the winner
            // and apply the exact same hash check as the non-racing path; otherwise a genuinely
            // different payload (different município/escopo) under the same key would silently
            // adopt the winner's job instead of conflicting.
            Job winner = jobRepository.findByIdempotency(
                            request.idempotencyPrincipal(), request.idempotencyKey())
                    .orElseThrow(() -> raced);
            if (!request.requestHash().equals(winner.requestHash())) {
                throw new JobRequestConflictException(
                        "idempotency key " + request.idempotencyKey()
                                + " was already used with a different request payload");
            }
            return winner;
        }
    }

    /**
     * SQLite's JDBC driver throws a plain {@code org.sqlite.SQLiteException} with no SQL state, so
     * Spring's default translator — there is no SQLite entry in {@code sql-error-codes.xml} —
     * cannot recognize it as {@link org.springframework.dao.DuplicateKeyException} the way it
     * would for Postgres/MySQL; it falls back to the generic {@code UncategorizedSQLException}
     * (confirmed empirically: {@code catch (DuplicateKeyException)} here never fired against real
     * SQLite). Detect the {@code SQLITE_CONSTRAINT_UNIQUE} condition the same way
     * {@link FailureClassifier} detects {@code SQLITE_BUSY} — by walking the cause chain for the
     * message text, since there is no portable exception type to catch instead.
     */
    private static boolean isIdempotencyKeyUniqueViolation(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                String message = sql.getMessage();
                if (message == null) {
                    return false;
                }
                String upper = message.toUpperCase(Locale.ROOT);
                // Not just any unique violation (e.g. jobs.job_id, the caller-supplied primary
                // key) — specifically the idempotency index, so an unrelated collision still
                // propagates instead of being misread as this race.
                return upper.contains("UNIQUE CONSTRAINT FAILED") && upper.contains("IDEMPOTENCY");
            }
        }
        return false;
    }
}
