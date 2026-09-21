package br.gov.observatorioaps.identityaccess.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;
import br.gov.observatorioaps.identityaccess.domain.Grant;
import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.identityaccess.domain.ScopeKind;
import br.gov.observatorioaps.identityaccess.domain.UserState;
import br.gov.observatorioaps.identityaccess.domain.GrantRepository;
import br.gov.observatorioaps.identityaccess.domain.UserRepository;
/**
 * Grant/revoke/block mutations behind {@code POST /users/{id}/grants}, {@code DELETE
 * /users/{id}/grants/{grantId}} and {@code POST /users/{id}/block}. Every mutation runs through
 * {@link AuthorizationVersionGuard#bumpAndRevokeSessions} (§1.12.7 L541) — including when the
 * target is the ACTOR's own account; there is no special-casing that would let a mutation skip
 * the invalidation the spec requires.
 *
 * <p>§1.12.6 L513 / ENG-45 ("impedir autoatribuição de escopo clínico"): this build refuses ANY
 * self-targeted grant, not only clinical ones — simpler to reason about and to test than
 * inspecting {@code role_permissions} per call, and strictly more conservative than the spec's
 * literal wording. A technical admin who needs a second role must have another
 * {@code manage_access} holder grant it.
 */
public final class AccessAdministrationService {

    private final UserRepository userRepository;
    private final GrantRepository grantRepository;
    private final AuthorizationVersionGuard authorizationVersionGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AccessAdministrationService(
            UserRepository userRepository, GrantRepository grantRepository,
            AuthorizationVersionGuard authorizationVersionGuard, TransactionTemplate transactionTemplate,
            Clock clock) {
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.authorizationVersionGuard = authorizationVersionGuard;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Grant grant(
            String targetUserId, Role role, ScopeKind scopeKind, String municipalityIbge,
            String cnes, String ine, String actorUserId) {
        if (targetUserId.equals(actorUserId)) {
            throw new SelfGrantForbiddenException("a caller cannot grant a role to their own account");
        }
        requireExists(targetUserId);
        validateScopeShape(scopeKind, municipalityIbge, cnes, ine);

        return transactionTemplate.execute(status -> {
            Grant newGrant = new Grant(
                    "grant-" + UUID.randomUUID(), targetUserId, role, scopeKind, municipalityIbge,
                    cnes, ine, clock.instant(), actorUserId, null, null);
            grantRepository.insert(newGrant);
            authorizationVersionGuard.bumpAndRevokeSessions(
                    targetUserId, "GRANT_ADDED", "role " + role + " granted by " + actorUserId, actorUserId);
            return newGrant;
        });
    }

    /**
     * Refuses a self-targeted revoke too, and not only for the general reason {@link #grant}
     * gives: revoking (unlike blocking) DOES set {@code revoked_at}, which
     * {@code BootstrapActivation.ensureBootstrapAdmin}'s idempotency check honors — a sole admin
     * revoking their own TECHNICAL_ADMIN grant would make that check see no active grant, and the
     * next boot would insert a second {@code username = 'admin'} row into a column with a UNIQUE
     * constraint (V3), crashing startup rather than merely locking the account out.
     *
     * @throws GrantNotFoundException if {@code grantId} is not an active grant of {@code targetUserId}.
     */
    public void revoke(String targetUserId, String grantId, String actorUserId) {
        if (targetUserId.equals(actorUserId)) {
            throw new SelfGrantForbiddenException("a caller cannot revoke a grant from their own account");
        }
        boolean belongsToTarget = grantRepository.activeGrantsForUser(targetUserId).stream()
                .anyMatch(g -> g.grantId().equals(grantId));
        if (!belongsToTarget) {
            throw new GrantNotFoundException("no active grant " + grantId + " for user " + targetUserId);
        }
        transactionTemplate.executeWithoutResult(status -> {
            grantRepository.revoke(grantId, clock.instant(), actorUserId);
            authorizationVersionGuard.bumpAndRevokeSessions(
                    targetUserId, "GRANT_REVOKED", "grant " + grantId + " revoked by " + actorUserId, actorUserId);
        });
    }

    /**
     * Refuses a self-targeted block for the same reason {@link #grant} refuses a self-targeted
     * grant, but for a sharper reason: {@code block} does not revoke the actor's own grant row, so
     * {@code BootstrapActivation.ensureBootstrapAdmin}'s idempotency check
     * ({@code anyExistsWithRole(TECHNICAL_ADMIN)}, which only looks at {@code revoked_at}, never
     * {@code state}) would keep seeing an active TECHNICAL_ADMIN grant and never mint a
     * replacement — a sole admin blocking themselves would brick the installation with no recovery
     * route in this recorte.
     */
    public void block(String targetUserId, String actorUserId) {
        if (targetUserId.equals(actorUserId)) {
            throw new SelfBlockForbiddenException("a caller cannot block their own account");
        }
        requireExists(targetUserId);
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.setState(targetUserId, UserState.BLOCKED);
            authorizationVersionGuard.bumpAndRevokeSessions(
                    targetUserId, "USER_BLOCKED", "blocked by " + actorUserId, actorUserId);
        });
    }

    public List<Grant> activeGrants(String userId) {
        return grantRepository.activeGrantsForUser(userId);
    }

    private void requireExists(String userId) {
        userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("unknown user: " + userId));
    }

    /** Mirrors the {@code user_grants} CHECK constraint in V3 — fail before the INSERT, not via SQLite's error. */
    private void validateScopeShape(ScopeKind scopeKind, String municipalityIbge, String cnes, String ine) {
        if (scopeKind == ScopeKind.INSTALLATION) {
            if (municipalityIbge != null || cnes != null || ine != null) {
                throw new IllegalArgumentException(
                        "INSTALLATION scope must not carry municipality_ibge/cnes/ine");
            }
        } else {
            if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
                throw new IllegalArgumentException(
                        "municipality_ibge must be a 7-digit code for MUNICIPALITY scope");
            }
        }
    }

    public static final class SelfGrantForbiddenException extends RuntimeException {
        public SelfGrantForbiddenException(String message) {
            super(message);
        }
    }

    public static final class SelfBlockForbiddenException extends RuntimeException {
        public SelfBlockForbiddenException(String message) {
            super(message);
        }
    }

    public static final class GrantNotFoundException extends RuntimeException {
        public GrantNotFoundException(String message) {
            super(message);
        }
    }

    public static final class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
}
