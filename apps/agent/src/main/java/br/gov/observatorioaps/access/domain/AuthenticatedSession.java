package br.gov.observatorioaps.access.domain;

import java.time.Instant;

/** A validated {@code sessions} row, returned only after every §1.12.7 check has passed. */
public record AuthenticatedSession(
        String sessionId,
        String userId,
        Instant loginAt,
        Instant lastInteractiveAt,
        Instant absoluteExpiresAt,
        long authorizationVersionAtLogin,
        Instant reauthAt
) {
}
