package br.gov.observatorioaps.jobrunner.domain;

import java.time.Instant;
/**
 * Inputs to create one job. {@code extractionId} present selects {@code IMMUTABLE_EXTRACT}
 * (§1.9.1) — replay of an already-finalized extract. {@code extractionId} absent selects {@code
 * LIVE_READ_ONLY} — a fresh PEC acquisition — and then {@code sourceId} is mandatory, since that
 * is the only way {@link br.gov.observatorioaps.jobrunner.application.IndicatorRunExecutor} can reconstruct
 * which PEC connection to open. A request naming neither is refused here, at request time, not
 * accepted and left to fail one poll cycle later inside {@code JobWorker}.
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
        boolean hasExtractionId = extractionId != null && !extractionId.isBlank();
        boolean hasSourceId = sourceId != null && !sourceId.isBlank();
        if (!hasExtractionId && !hasSourceId) {
            throw new IllegalArgumentException(
                    "either extractionId (IMMUTABLE_EXTRACT) or sourceId (LIVE_READ_ONLY) is required");
        }
    }
}
