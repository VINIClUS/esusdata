package esusdata.run.worker;

import esusdata.run.job.ActiveJobExistsException;
import esusdata.run.job.EnqueueRequest;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.job.JobRequestConflictException;
import esusdata.run.job.SqliteUniqueViolation;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataAccessException;

/**
 * ENG-24: "mesmo pedido/chave reutiliza job somente com autorização atual; payload/município/
 * escopo diferente conflita; nova aquisição retificada gera nova entrada/run." Separates request
 * idempotency (this class) from data identity (§1.9.5) — a request without a key always creates a
 * new job.
 */
public final class IdempotencyResolver {

    private static final String IDEMPOTENCY_INDEX_COLUMN = "jobs.idempotency_key";

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

        var existing = jobRepository.findByIdempotency(request.idempotencyPrincipal(), request.idempotencyKey());
        if (existing.isPresent()) {
            Job job = existing.get();
            boolean expired = job.idempotencyExpiresAt() != null
                    && job.idempotencyExpiresAt().isBefore(clock.instant());
            if (!expired) {
                requireSameRequest(request, job);
                return job; // identical repetition — same job, current authorization already
                // re-checked by whatever authorizes the caller before reaching here
            }
            // Expired: free the key so the unique index (which has no notion of expiry) accepts
            // a fresh job under the same (principal, key) pair.
            jobRepository.clearIdempotencyKey(job.jobId());
        }

        try {
            return jobRepository.enqueue(request);
        } catch (ActiveJobExistsException active) {
            // A concurrent submission under the same key can trip V7's active-job index before
            // the idempotency index; the key's own winner, when there is one, is the answer.
            return sameKeyWinner(request).orElseThrow(() -> active);
        } catch (DataAccessException raced) {
            // Not just any unique violation (e.g. jobs.job_id, the caller-supplied primary key) —
            // specifically the idempotency index, so an unrelated collision still propagates.
            if (!SqliteUniqueViolation.on(raced, IDEMPOTENCY_INDEX_COLUMN)) {
                throw raced;
            }
            // Lost a race against a concurrent submission under the same key — both requests
            // passed the findByIdempotency check above before either inserted. Reload the winner
            // and apply the exact same hash check as the non-racing path; otherwise a genuinely
            // different payload (different município/escopo) under the same key would silently
            // adopt the winner's job instead of conflicting.
            return sameKeyWinner(request).orElseThrow(() -> raced);
        }
    }

    private Optional<Job> sameKeyWinner(EnqueueRequest request) {
        Optional<Job> winner =
                jobRepository.findByIdempotency(request.idempotencyPrincipal(), request.idempotencyKey());
        winner.ifPresent(job -> requireSameRequest(request, job));
        return winner;
    }

    private static void requireSameRequest(EnqueueRequest request, Job job) {
        if (!request.requestHash().equals(job.requestHash())) {
            throw new JobRequestConflictException("idempotency key " + request.idempotencyKey()
                    + " was already used with a different request payload");
        }
    }
}
