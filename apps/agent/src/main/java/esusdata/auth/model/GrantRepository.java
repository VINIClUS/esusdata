package esusdata.auth.model;

import java.time.Instant;
import java.util.List;

/**
 * Persists {@code user_grants}. {@link #activeGrantsForUser} is the only read path
 * {@code ScopeResolver} uses — "concessões atuais" (§1.7.3 L274) means {@code revoked_at is null},
 * queried fresh every time, never cached.
 */
public interface GrantRepository {
    void insert(Grant grant);

    List<Grant> activeGrantsForUser(String userId);

    /** Revokes a grant; {@code true} only if it was active and this call revoked it. */
    boolean revoke(String grantId, Instant at, String revokedBy);

    void revokeAllForUser(String userId, Instant at, String revokedBy);
}
