package br.gov.observatorioaps.jobrunner;

import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;

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
        } catch (DuplicateKeyException raced) {
            // Lost a race against a concurrent identical submission under the same key.
            return jobRepository.findByIdempotency(
                            request.idempotencyPrincipal(), request.idempotencyKey())
                    .orElseThrow(() -> raced);
        }
    }
}
