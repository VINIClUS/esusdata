package br.gov.observatorioaps.identityaccess;

import br.gov.observatorioaps.config.SqliteDataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.12.7 L533: an absolute session duration counted from login, independent of activity —
 * "8 horas desde o login expiram mesmo com atividade contínua." Every {@link SessionService}
 * method takes {@code Instant now} as a parameter rather than a mutable clock, so the whole
 * 8-hour span is driven by explicit instants, not real elapsed wall-clock time.
 */
class SessionAbsoluteDurationTest {

    private static final String USER_ID = "user-1";

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private SessionService sessionService;
    private Instant loginAt;
    private String rawToken;

    @BeforeEach
    void setUp() {
        loginAt = Instant.parse("2026-09-20T08:00:00Z");
        context = new AnnotationConfigApplicationContext();
        context.register(SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.resolve("db").toString())));
        context.refresh();

        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        UserRepository userRepository = new UserRepository(jdbc);
        userRepository.insert(new UserAccount(
                USER_ID, "user-1", "User One", "irrelevant-hash", "ARGON2ID", "{}", "v1", 1,
                UserState.ACTIVE, loginAt, "test-fixture", null));

        // inactivityMinutes=15, absoluteDurationHours=8 — the spec's own baseline.
        SecurityProperties properties =
                new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 8, 1, 1, 16, 32, "v1", 30, 24);
        sessionService = new SessionService(jdbc, userRepository, properties);
        rawToken = sessionService.create(USER_ID, 1, loginAt);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void remainsValidJustBeforeTheEightHourAbsoluteDurationDespiteContinuousActivity() {
        warmUpEveryTenMinutesUpTo(Duration.ofHours(7).plus(Duration.ofMinutes(55)));

        Instant justBeforeEightHours = loginAt.plus(Duration.ofHours(8)).minusSeconds(1);
        assertThat(sessionService.validate(rawToken, justBeforeEightHours, true)).isPresent();
    }

    @Test
    void expiresAtExactlyEightHoursFromLoginEvenWithRecentInteractiveActivity() {
        warmUpEveryTenMinutesUpTo(Duration.ofHours(7).plus(Duration.ofMinutes(55)));

        Instant eightHoursFromLogin = loginAt.plus(Duration.ofHours(8));
        // Well within the 15-minute inactivity window (last touch was 5 minutes before this
        // instant) — if this were empty only because of inactivity, the test would be worthless.
        // It must be empty because of the ABSOLUTE duration specifically.
        assertThat(sessionService.validate(rawToken, eightHoursFromLogin, true)).isEmpty();
    }

    @Test
    void neverExtendsPastEightHoursNoMatterHowManyInteractiveTouchesPrecededIt() {
        // Touch every 10 minutes (well under the 15-minute inactivity ceiling) for the entire
        // first 7 hours and 50 minutes — continuous activity, not a single lucky touch.
        warmUpEveryTenMinutesUpTo(Duration.ofHours(7).plus(Duration.ofMinutes(50)));

        Instant eightHoursFromLogin = loginAt.plus(Duration.ofHours(8));
        assertThat(sessionService.validate(rawToken, eightHoursFromLogin, true)).isEmpty();
    }

    @Test
    void revalidateAgreesWithValidateOnTheAbsoluteDurationBoundary() {
        // SessionService.revalidate (fatia D's SSE background check) must not diverge from
        // validate() on this boundary — same underlying isCurrentlyValid check, addressed by
        // session id instead of the raw token.
        warmUpEveryTenMinutesUpTo(Duration.ofHours(7).plus(Duration.ofMinutes(55)));
        String sessionId = sha256Hex(rawToken);

        Instant eightHoursFromLogin = loginAt.plus(Duration.ofHours(8));
        assertThat(sessionService.revalidate(sessionId, eightHoursFromLogin)).isFalse();
    }

    /**
     * Continuous interactive activity from login up to {@code upTo}, at intervals well under the
     * 15-minute inactivity ceiling — a single big jump from login would trip the (unrelated)
     * inactivity check before the absolute-duration boundary is ever reached.
     */
    private void warmUpEveryTenMinutesUpTo(Duration upTo) {
        for (long minute = 10; minute <= upTo.toMinutes(); minute += 10) {
            Instant tick = loginAt.plus(Duration.ofMinutes(minute));
            assertThat(sessionService.validate(rawToken, tick, true))
                    .as("expected the session to still be valid at minute %d", minute)
                    .isPresent();
        }
    }

    private String sha256Hex(String rawTokenValue) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(rawTokenValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
