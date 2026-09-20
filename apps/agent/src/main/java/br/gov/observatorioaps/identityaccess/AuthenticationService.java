package br.gov.observatorioaps.identityaccess;

import java.time.Clock;
import java.time.Instant;

/**
 * Orchestrates login/logout over the lower-level identity-access pieces. Deliberately framework-
 * free (no {@code jakarta.servlet} import anywhere in this package) — {@code api} translates the
 * raw token this returns into a cookie, and a request into {@code origin}/{@code now}.
 */
public final class AuthenticationService {

    private final UserRepository userRepository;
    private final Argon2Profile argon2Profile;
    private final SessionService sessionService;
    private final LoginThrottle loginThrottle;
    private final AuthAuditWriter auditWriter;
    private final Clock clock;

    public AuthenticationService(
            UserRepository userRepository, Argon2Profile argon2Profile, SessionService sessionService,
            LoginThrottle loginThrottle, AuthAuditWriter auditWriter, Clock clock) {
        this.userRepository = userRepository;
        this.argon2Profile = argon2Profile;
        this.sessionService = sessionService;
        this.loginThrottle = loginThrottle;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    public record LoginResult(String rawToken, String userId, String displayName) {
    }

    /**
     * §1.12.7 L538: throttle is checked before any password comparison — a throttled account
     * never reaches Argon2id verification, so the CPU cost of a lockout is negligible for the
     * attacker's target and irrelevant to a legitimate user retrying after the delay.
     */
    public LoginResult login(String username, String password, String origin, Instant now) {
        loginThrottle.checkAllowed(username, origin, now);
        boolean success = false;
        try {
            UserAccount user = userRepository.findByUsername(username).orElse(null);
            if (user == null || user.state() != UserState.ACTIVE
                    || !argon2Profile.matches(password, user.passwordHash())) {
                throw new AuthenticationFailedException("invalid username or password");
            }
            success = true;
            userRepository.recordLogin(user.userId(), now);
            String rawToken = sessionService.create(user.userId(), user.authorizationVersion(), now);
            auditWriter.record(now, user.userId(), "LOGIN", user.userId(), "SUCCESS", null);
            return new LoginResult(rawToken, user.userId(), user.displayName());
        } finally {
            loginThrottle.recordAttempt(username, origin, success, now);
            if (!success) {
                auditWriter.record(now, null, "LOGIN", username, "FAILED", null);
            }
        }
    }

    public void logout(String sessionId, String userId, Instant now) {
        sessionService.revoke(sessionId, now, "logout");
        auditWriter.record(now, userId, "LOGOUT", userId, "SUCCESS", null);
    }

    /**
     * §1.12.7 L539: the explicit re-verification a sensitive action (grant, revoke, source secret
     * change) requires within the last five minutes. Advances only {@code reauth_at} on the
     * CURRENT session row — the session's own inactivity/absolute-duration checks are untouched,
     * and the effect is visible only on the NEXT request that re-validates the session (the
     * {@link AuthenticatedSession} already materialized for THIS request is not mutated in place).
     */
    public void reauthenticate(
            String sessionId, String userId, String password, String origin, Instant now) {
        UserAccount user = userRepository.findById(userId).orElse(null);
        String username = user == null ? userId : user.username();
        loginThrottle.checkAllowed(username, origin, now);
        boolean success = false;
        try {
            if (user == null || user.state() != UserState.ACTIVE
                    || !argon2Profile.matches(password, user.passwordHash())) {
                throw new AuthenticationFailedException("invalid password");
            }
            success = true;
            sessionService.touchReauth(sessionId, now);
            auditWriter.record(now, userId, "REAUTH", userId, "SUCCESS", null);
        } finally {
            loginThrottle.recordAttempt(username, origin, success, now);
            if (!success) {
                auditWriter.record(now, userId, "REAUTH", userId, "FAILED", null);
            }
        }
    }
}
