package esusdata.run.job;

import java.time.Instant;

/** A {@code jobs} row (§1.9.4's minimum field set, plus the §1.9.5 idempotency binding). */
public record Job(
        String jobId,
        String runId,
        String municipalityIbge,
        String indicatorPack,
        String ruleVersion,
        String referencePeriod,
        JobState state,
        int attempt,
        int maxAttempts,
        String processInstanceId,
        long executionGeneration,
        Instant lastProgressAt,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String failureCode,
        String failureDetail,
        String extractionId,
        String idempotencyKey,
        String sourceId,
        String requestedScopeJson,
        String idempotencyPrincipal,
        String requestHash,
        Instant idempotencyExpiresAt,
        String stagingId,
        Instant cancelRequestedAt
) {
    /** {@code extraction_id} present means the job replays an already-finalized extract. */
    public boolean isImmutableExtract() {
        return extractionId != null;
    }
}
