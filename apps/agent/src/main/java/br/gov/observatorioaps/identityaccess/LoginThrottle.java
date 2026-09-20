package br.gov.observatorioaps.identityaccess;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * §1.12.7 L538: "cinco falhas por conta em quinze minutos iniciam atraso progressivo com teto de
 * quinze minutos; limite adicional por origem, sem bloquear indefinidamente todos os usuários
 * atrás do mesmo NAT." State is derived from {@code login_attempts} on every check — no in-memory
 * counter that would reset on restart and lose the throttle it was protecting.
 *
 * <p>The origin-level limit uses a materially higher threshold than the per-account one and never
 * blocks longer than the per-account ceiling — the spec is explicit that shared-NAT origins must
 * not become an indefinite lockout for every user behind them.
 */
public final class LoginThrottle {

    private static final int ORIGIN_FAILURE_MULTIPLIER = 4;

    private final JdbcTemplate jdbc;
    private final SecurityProperties properties;

    public LoginThrottle(JdbcTemplate jdbc, SecurityProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public void checkAllowed(String username, String origin, Instant now) {
        Duration window = Duration.ofMinutes(properties.throttleWindowMinutes());
        Instant windowStart = now.minus(window);

        Optional<Instant> accountDelay = delayUntil(
                "username", username, windowStart, now, properties.maxLoginAttempts());
        if (accountDelay.isPresent() && accountDelay.get().isAfter(now)) {
            throw new LoginThrottledException(accountDelay.get());
        }
        Optional<Instant> originDelay = delayUntil(
                "origin", origin, windowStart, now, properties.maxLoginAttempts() * ORIGIN_FAILURE_MULTIPLIER);
        if (originDelay.isPresent() && originDelay.get().isAfter(now)) {
            throw new LoginThrottledException(originDelay.get());
        }
    }

    public void recordAttempt(String username, String origin, boolean succeeded, Instant now) {
        jdbc.update("""
                INSERT INTO login_attempts (attempt_id, username, origin, attempted_at, outcome)
                VALUES (?,?,?,?,?)
                """, "attempt-" + java.util.UUID.randomUUID(), username, origin, now.toString(),
                succeeded ? "SUCCEEDED" : "FAILED");
    }

    private Optional<Instant> delayUntil(
            String column, String value, Instant windowStart, Instant now, int threshold) {
        if (value == null) {
            return Optional.empty();
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from login_attempts where " + column + " = ? "
                        + "and outcome = 'FAILED' and attempted_at >= ?",
                Integer.class, value, windowStart.toString());
        if (count == null || count < threshold) {
            return Optional.empty();
        }
        String lastFailureAt = jdbc.queryForObject(
                "select max(attempted_at) from login_attempts where " + column + " = ? and outcome = 'FAILED'",
                String.class, value);
        if (lastFailureAt == null) {
            return Optional.empty();
        }
        long excess = count - threshold + 1;
        long delayMinutes = Math.min(properties.throttleCeilingMinutes(), excess);
        return Optional.of(Instant.parse(lastFailureAt).plus(Duration.ofMinutes(delayMinutes)));
    }

    public static final class LoginThrottledException extends RuntimeException {
        private final Instant retryAfter;

        public LoginThrottledException(Instant retryAfter) {
            super("too many failed login attempts; retry after " + retryAfter);
            this.retryAfter = retryAfter;
        }

        public Instant retryAfter() {
            return retryAfter;
        }
    }
}
