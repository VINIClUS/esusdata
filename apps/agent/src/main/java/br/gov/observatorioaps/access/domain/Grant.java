package br.gov.observatorioaps.access.domain;

import java.time.Instant;

/**
 * A {@code user_grants} row. {@code revokedAt == null} is what "concessão atual" (§1.7.3 L274)
 * means — the row itself is never deleted, so revocation stays auditable.
 */
public record Grant(
        String grantId,
        String userId,
        Role role,
        ScopeKind scopeKind,
        String municipalityIbge,
        String cnes,
        String ine,
        Instant grantedAt,
        String grantedBy,
        Instant revokedAt,
        String revokedBy
) {
    public boolean isActive() {
        return revokedAt == null;
    }
}
