package br.gov.observatorioaps.access.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists {@code users}. Every read returns the row exactly as stored — no caching, no session
 * copy — because §1.7.3 L274 requires authorization decisions to use the user's CURRENT state,
 * never a snapshot taken at login.
 */
public interface UserRepository {
    void insert(UserAccount user);

    Optional<UserAccount> findById(String userId);

    Optional<UserAccount> findByUsername(String username);

    boolean anyExistsWithRole(Role role);

    void recordLogin(String userId, Instant at);

    void setPassword(String userId, String passwordHash, String passwordAlgo,
            String passwordParamsJson, String securityPolicyVersion);

    void setState(String userId, UserState state);

    /** @return the new {@code authorization_version} */
    long bumpAuthorizationVersion(String userId);
}
