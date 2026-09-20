package br.gov.observatorioaps.identityaccess;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Deliberately NOT backed by {@code HttpSession}: §1.12.7 needs two independent expiry clocks
 * (inactivity vs. absolute duration) that "polling, SSE e heartbeat não contam" toward, and a
 * servlet container's own session timeout cannot express that distinction — every request touches
 * {@code HttpSession#getLastAccessedTime()} regardless of whether it was an interactive action.
 * Backing sessions by this table instead also means a session survives process restart the same
 * way the job queue already does, rather than vanishing with in-memory {@code HttpSession} state.
 *
 * <p>Only the SHA-256 hash of the opaque cookie value is ever persisted — the raw token exists
 * only in the response Set-Cookie header and the caller's memory.
 */
public final class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RowMapper<Row> MAPPER = (rs, rowNum) -> new Row(
            rs.getString("session_id"), rs.getString("user_id"),
            Instant.parse(rs.getString("login_at")), Instant.parse(rs.getString("last_interactive_at")),
            Instant.parse(rs.getString("absolute_expires_at")), rs.getLong("authorization_version_at_login"),
            rs.getString("reauth_at") == null ? null : Instant.parse(rs.getString("reauth_at")),
            rs.getString("revoked_at"));

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final SecurityProperties properties;

    public SessionService(JdbcTemplate jdbc, UserRepository userRepository, SecurityProperties properties) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** @return the raw opaque token — set as the session cookie value, never stored. */
    public String create(String userId, long authorizationVersionAtLogin, Instant now) {
        String rawToken = newOpaqueToken();
        String sessionId = hash(rawToken);
        Instant absoluteExpiresAt = now.plus(Duration.ofHours(properties.absoluteDurationHours()));
        jdbc.update("""
                INSERT INTO sessions (session_id, user_id, login_at, last_interactive_at,
                    absolute_expires_at, authorization_version_at_login, reauth_at, revoked_at, revoked_reason)
                VALUES (?,?,?,?,?,?,null,null,null)
                """,
                sessionId, userId, now.toString(), now.toString(), absoluteExpiresAt.toString(),
                authorizationVersionAtLogin);
        return rawToken;
    }

    /**
     * Validates a presented token against every independent check in §1.12.7. {@code interactive}
     * controls whether THIS request advances {@code last_interactive_at} — SSE/heartbeat/progress
     * polling callers must pass {@code false} (ENG-44: "polling não prolonga sessão").
     */
    public Optional<AuthenticatedSession> validate(String rawToken, Instant now, boolean interactive) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String sessionId = hash(rawToken);
        Row row = findRow(sessionId);
        if (row == null || !isCurrentlyValid(row, now)) {
            return Optional.empty();
        }
        if (interactive) {
            jdbc.update("update sessions set last_interactive_at = ? where session_id = ?",
                    now.toString(), sessionId);
        }
        return Optional.of(new AuthenticatedSession(
                sessionId, row.userId(), row.loginAt(), interactive ? now : row.lastInteractiveAt(),
                row.absoluteExpiresAt(), row.authorizationVersionAtLogin(), row.reauthAt()));
    }

    /**
     * Same validity checks as {@link #validate}, addressed by {@code sessionId} rather than the
     * raw token — for background revalidation (SSE, fatia D) where only the hashed session id
     * survived the initial handshake. Never touches {@code last_interactive_at}: a periodic
     * background check is exactly the "polling não prolonga sessão" case ENG-44 describes, not an
     * interactive action.
     */
    public boolean revalidate(String sessionId, Instant now) {
        Row row = findRow(sessionId);
        return row != null && isCurrentlyValid(row, now);
    }

    private Row findRow(String sessionId) {
        return jdbc.query("select * from sessions where session_id = ?", MAPPER, sessionId)
                .stream().findFirst().orElse(null);
    }

    private boolean isCurrentlyValid(Row row, Instant now) {
        if (row.revokedAt() != null) {
            return false;
        }
        if (!row.absoluteExpiresAt().isAfter(now)) {
            return false;
        }
        Instant inactivityDeadline = row.lastInteractiveAt().plus(Duration.ofMinutes(properties.inactivityMinutes()));
        if (!inactivityDeadline.isAfter(now)) {
            return false;
        }
        UserAccount user = userRepository.findById(row.userId()).orElse(null);
        if (user == null || user.state() != UserState.ACTIVE) {
            return false;
        }
        // §1.12.7 L541: a role/scope/reset/block change since login invalidates the session —
        // revalidated here on every use, not only when AuthorizationVersionGuard runs.
        return user.authorizationVersion() == row.authorizationVersionAtLogin();
    }

    public void touchReauth(String sessionId, Instant now) {
        jdbc.update("update sessions set reauth_at = ? where session_id = ?", now.toString(), sessionId);
    }

    /** §1.12.7: "Reautenticação realizada nos últimos cinco minutos" — for sensitive actions. */
    public boolean reauthenticatedRecently(AuthenticatedSession session, Instant now) {
        if (session.reauthAt() == null) {
            return false;
        }
        return session.reauthAt().plus(Duration.ofMinutes(properties.reauthWindowMinutes())).isAfter(now);
    }

    public void revoke(String sessionId, Instant now, String reason) {
        jdbc.update("update sessions set revoked_at = ?, revoked_reason = ? where session_id = ?",
                now.toString(), reason, sessionId);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Row(
            String sessionId, String userId, Instant loginAt, Instant lastInteractiveAt,
            Instant absoluteExpiresAt, long authorizationVersionAtLogin, Instant reauthAt, String revokedAt) {
    }
}
