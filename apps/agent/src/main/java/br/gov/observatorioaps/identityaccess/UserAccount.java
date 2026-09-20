package br.gov.observatorioaps.identityaccess;

import java.time.Instant;

/**
 * A {@code users} row. {@code passwordParamsJson} records the Argon2id parameters that were
 * EFFECTIVE when {@code passwordHash} was computed (§1.12.7 L534: "registrar parâmetros
 * efetivos") — independent of whatever the current baseline in {@code application.yml} says, so a
 * later baseline change can never silently reinterpret an old hash under new parameters.
 */
public record UserAccount(
        String userId,
        String username,
        String displayName,
        String passwordHash,
        String passwordAlgo,
        String passwordParamsJson,
        String securityPolicyVersion,
        long authorizationVersion,
        UserState state,
        Instant createdAt,
        String createdBy,
        Instant lastLoginAt
) {
}
