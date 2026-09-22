package esusdata.auth;

import esusdata.auth.model.UserAccount;
import esusdata.auth.model.UserState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import esusdata.web.ApiFixtureSupport;
/** Reauthentication must use the same account/origin progressive throttle as login. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReauthThrottleApiTest extends ApiFixtureSupport {

    @Autowired
    Argon2Profile argon2Profile;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void wrongReauthenticationIsThrottledByAccountAndOrigin() throws Exception {
        String userId = "reauth-throttle-" + System.nanoTime();
        String password = "a-strong-enough-passphrase-1";
        userRepository.insert(new UserAccount(
                userId, userId, userId, argon2Profile.encode(password), Argon2Profile.ALGO,
                argon2Profile.effectiveParamsJson(), "v1", 1, UserState.ACTIVE,
                clock.instant(), "test-fixture", null));
        String cookie = sessionCookie(userId);

        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> failed = authenticatedPost(cookie,
                    URI.create(BASE_URL + "/api/v1/auth/reauth"),
                    "{\"password\":\"wrong-reauth-password\"}");
            assertThat(failed.statusCode()).isEqualTo(401);
            assertThat(failed.body()).contains("AUTHENTICATION_FAILED");
        }

        HttpResponse<String> throttled = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/auth/reauth"),
                "{\"password\":\"wrong-reauth-password\"}");
        assertThat(throttled.statusCode()).isEqualTo(429);
        assertThat(throttled.body()).contains("LOGIN_THROTTLED");

        List<Map<String, Object>> reauthAudit = jdbc.queryForList(
                "select outcome from auth_audit"
                        + " where actor_user_id = ? and event_type = 'REAUTH' and target = ?",
                userId, userId);
        assertThat(reauthAudit).hasSize(6);
        assertThat(reauthAudit).anyMatch(row -> "THROTTLED".equals(row.get("outcome")));

        List<Map<String, Object>> attempts = jdbc.queryForList(
                "select outcome from login_attempts where username = ?", userId);
        assertThat(attempts).hasSize(6);
        assertThat(attempts).anyMatch(row -> "THROTTLED".equals(row.get("outcome")));
    }
}
