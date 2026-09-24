package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.config.SqliteConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * §1.12.7 L538: "cinco falhas por conta em quinze minutos iniciam atraso progressivo com teto de
 * quinze minutos; limite adicional por origem, sem bloquear indefinidamente todos os usuários
 * atrás do mesmo NAT." Real SQLite {@code login_attempts} table (via {@link SqliteConfig}
 * — Flyway runs on context refresh) — the throttle's whole point is state that survives a
 * restart, so an in-memory fake would prove nothing about that.
 */
class LoginThrottleTest {

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SecurityProperties properties;
    private LoginThrottle throttle;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-20T12:00:00Z");
        context = new AnnotationConfigApplicationContext();
        context.register(SqliteConfig.class);
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "test",
                        Map.of(
                                "observatorio.data.directory",
                                dataDir.resolve("db").toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        // maxLoginAttempts=5, throttleWindowMinutes=15, throttleCeilingMinutes=15 — the spec's
        // own baseline, not a shortened test value.
        properties = new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 8, 1, 1, 16, 32, "v1", 30, 24);
        throttle = new LoginThrottle(jdbc, properties);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void fourFailuresDoNotThrottleTheFifthAttempt() {
        recordFailures("alice", "203.0.113.1", 4);

        assertThatCode(() -> throttle.checkAllowed("alice", "203.0.113.1", now)).doesNotThrowAnyException();
    }

    @Test
    void theFifthFailureWithinTheWindowStartsProgressiveDelay() {
        recordFailures("alice", "203.0.113.1", 5);

        assertThatThrownBy(() -> throttle.checkAllowed("alice", "203.0.113.1", now))
                .isInstanceOf(LoginThrottle.LoginThrottledException.class);
    }

    @Test
    void theDelayEndsAndACleanCheckSucceedsOnceTheRetryAfterInstantHasPassed() {
        recordFailures("alice", "203.0.113.1", 5);

        LoginThrottle.LoginThrottledException thrown =
                catchThrottled(() -> throttle.checkAllowed("alice", "203.0.113.1", now));

        assertThatCode(() -> throttle.checkAllowed(
                        "alice", "203.0.113.1", thrown.retryAfter().plusSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheFifteenMinuteWindowDoNotCount() {
        Instant longAgo = now.minus(Duration.ofMinutes(16));
        recordFailuresAt("alice", "203.0.113.1", 5, longAgo);

        assertThatCode(() -> throttle.checkAllowed("alice", "203.0.113.1", now)).doesNotThrowAnyException();
    }

    @Test
    void theDelayCeilingIsFifteenMinutesRegardlessOfHowManyExcessFailuresAccumulate() {
        // 5 is the threshold; excess = count - threshold + 1. At 25 failures, excess = 21 minutes
        // of raw delay — must be capped at throttleCeilingMinutes (15), not applied uncapped.
        recordFailures("alice", "203.0.113.1", 25);

        LoginThrottle.LoginThrottledException thrown =
                catchThrottled(() -> throttle.checkAllowed("alice", "203.0.113.1", now));

        Instant lastFailureAt = now; // recordFailures uses `now` for every attempt in this test
        assertThat(thrown.retryAfter()).isEqualTo(lastFailureAt.plus(Duration.ofMinutes(15)));
    }

    @Test
    void anAccountWithNoFailuresIsNeverThrottledByAnUnrelatedAccountsFailures() {
        recordFailures("bob", "203.0.113.1", 10);

        assertThatCode(() -> throttle.checkAllowed("alice", "203.0.113.2", now)).doesNotThrowAnyException();
    }

    @Test
    void theOriginLimitIsFourTimesHigherThanTheAccountLimitAndNeverBlocksLongerThanItsCeiling() {
        // 5 different accounts, all sharing one origin (simulating one NAT), each failing 4 times
        // (under their OWN per-account threshold of 5) — 20 failures on the shared origin, at 4x
        // the per-account threshold (20), crossing the origin threshold and throttling the origin,
        // but never indefinitely: still bounded by the same 15-minute ceiling as the account path.
        for (int account = 0; account < 5; account++) {
            recordFailures("user-" + account, "203.0.113.1", 4);
        }

        LoginThrottle.LoginThrottledException thrown =
                catchThrottled(() -> throttle.checkAllowed("user-999", "203.0.113.1", now));

        assertThat(thrown.retryAfter())
                .isBeforeOrEqualTo(now.plus(Duration.ofMinutes(properties.throttleCeilingMinutes())));
        assertThatCode(() -> throttle.checkAllowed(
                        "user-999", "203.0.113.1", thrown.retryAfter().plusSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void anOriginThrottleNeverBlocksAnAccountBehindADifferentOrigin() {
        // Same shared-NAT scenario as above, but a fresh account arriving through its OWN origin
        // must not be swept up in the shared origin's throttle — "sem bloquear indefinidamente
        // todos os usuários atrás do mesmo NAT" is specifically about NOT punishing everyone.
        for (int account = 0; account < 5; account++) {
            recordFailures("user-" + account, "203.0.113.1", 4);
        }

        assertThatCode(() -> throttle.checkAllowed("user-999", "198.51.100.1", now))
                .doesNotThrowAnyException();
    }

    private void recordFailures(String username, String origin, int count) {
        recordFailuresAt(username, origin, count, now);
    }

    private void recordFailuresAt(String username, String origin, int count, Instant at) {
        for (int i = 0; i < count; i++) {
            throttle.recordAttempt(username, origin, false, at);
        }
    }

    private LoginThrottle.LoginThrottledException catchThrottled(Runnable action) {
        try {
            action.run();
        } catch (LoginThrottle.LoginThrottledException e) {
            return e;
        }
        throw new AssertionError("expected a LoginThrottledException but none was thrown");
    }
}
