package br.gov.observatorioaps.jobrunner;

import java.time.Instant;

/**
 * Inputs to create one job. {@code extractionId} present selects {@code IMMUTABLE_EXTRACT}
 * (§1.9.1) — the only acquisition mode this phase implements. {@code LIVE_READ_ONLY} (fresh PEC
 * acquisition from a queued job) is not implemented yet; rejecting a null {@code extractionId}
 * here, at request time, is deliberate — a request for an unsupported mode is refused where it is
 * made, not accepted and left to fail one poll cycle later inside {@code JobWorker}.
 */
public record EnqueueRequest(
        String jobId,
        String runId,
        String municipalityIbge,
        String indicatorPack,
        String ruleVersion,
        String referencePeriod,
        int maxAttempts,
        String sourceId,
        String extractionId,
        String idempotencyPrincipal,
        String idempotencyKey,
        String requestHash,
        Instant idempotencyExpiresAt,
        String requestedScopeJson,
        Instant createdAt
) {
    public EnqueueRequest {
        if (extractionId == null || extractionId.isBlank()) {
            throw new IllegalArgumentException(
                    "LIVE_READ_ONLY acquisition is not implemented in this phase — "
                            + "extractionId (IMMUTABLE_EXTRACT) is required");
        }
    }
}
