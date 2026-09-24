package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.web.SecuritySliceTestSupport;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The end-to-end proof this slice exists for: activate the bootstrap admin, log in, make an
 * authenticated call, log out, and confirm the session is actually gone afterward — all through
 * real HTTP (cookies, CSRF header, Origin/Host validation), not MockMvc's simulated request.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuthRoundTripTest extends SecuritySliceTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void activateLoginCallMeAndLogoutEndToEnd() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        // Bootstrap the CSRF cookie (also proves /ready doubles as the SPA's first request).
        client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);

        String activationToken = readActivationToken();
        String password = "a-strong-enough-passphrase-1";

        HttpResponse<String> activate = post(
                client,
                "/api/v1/auth/activate",
                csrfToken,
                "{\"token\":\"" + activationToken + "\",\"password\":\"" + password + "\"}");
        assertThat(activate.statusCode()).isEqualTo(204);

        HttpResponse<String> login = post(
                client, "/api/v1/auth/login", csrfToken, "{\"username\":\"admin\",\"password\":\"" + password + "\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("OBS_SESSION=") && cookie.contains("HttpOnly"));
        String adminUserId = extractField(login.body(), "userId");

        HttpResponse<String> me = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("userId");
        // Regression guard: an authenticated request must never delete the CSRF cookie it already
        // issued — SessionManagementFilter's CsrfAuthenticationStrategy rotation firing on every
        // request (not just login) is exactly the bug this slice hunted down.
        assertThat(me.headers().allValues("Set-Cookie"))
                .noneMatch(cookie ->
                        cookie.startsWith("XSRF-TOKEN=") && (cookie.contains("Max-Age=0") || cookie.contains("1970")));

        // §1.12.7 L539: a sensitive admin mutation is refused until reauthentication, then
        // proceeds — proving POST /api/v1/auth/reauth is genuinely wired to
        // ReauthenticationGuard, not merely present and untested.
        String grantBody = "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\"3541307\"}";
        HttpResponse<String> grantBeforeReauth =
                post(client, "/api/v1/users/" + adminUserId + "/grants", csrfToken, grantBody);
        assertThat(grantBeforeReauth.statusCode()).isEqualTo(401);
        assertThat(grantBeforeReauth.body()).contains("REAUTHENTICATION_REQUIRED");

        HttpResponse<String> wrongReauth =
                post(client, "/api/v1/auth/reauth", csrfToken, "{\"password\":\"definitely-the-wrong-passphrase\"}");
        assertThat(wrongReauth.statusCode()).isEqualTo(401);
        assertThat(wrongReauth.body()).contains("AUTHENTICATION_FAILED");

        HttpResponse<String> reauth =
                post(client, "/api/v1/auth/reauth", csrfToken, "{\"password\":\"" + password + "\"}");
        assertThat(reauth.statusCode()).isEqualTo(204);

        // Refused for a DIFFERENT reason now (ENG-45 self-grant) — 403, not 401 — which is exactly
        // how we know the reauth gate itself was actually passed, not merely bypassed by a bug.
        HttpResponse<String> grantAfterReauth =
                post(client, "/api/v1/users/" + adminUserId + "/grants", csrfToken, grantBody);
        assertThat(grantAfterReauth.statusCode()).isEqualTo(403);
        assertThat(grantAfterReauth.body()).contains("SELF_GRANT_FORBIDDEN");

        List<Map<String, Object>> reauthAudit = jdbc.queryForList(
                "select outcome, detail_json from auth_audit"
                        + " where actor_user_id = ? and event_type = 'REAUTH' order by at",
                adminUserId);
        assertThat(reauthAudit).anyMatch(row -> "FAILED".equals(row.get("outcome")));
        assertThat(reauthAudit).anyMatch(row -> "SUCCESS".equals(row.get("outcome")));
        reauthAudit.forEach(row -> assertThat(String.valueOf(row.get("detail_json")))
                .doesNotContain("definitely-the-wrong-passphrase")
                .doesNotContain(password));

        HttpResponse<String> logout = post(client, "/api/v1/auth/logout", csrfToken, null);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> meAfterLogout = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(HttpClient client, String path, String csrfToken, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken);
        builder = body == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody())
                : builder.POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrfTokenFrom(CookieManager cookieManager) {
        CookieStore store = cookieManager.getCookieStore();
        for (HttpCookie cookie : store.getCookies()) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new IllegalStateException("no XSRF-TOKEN cookie was issued");
    }

    private String readActivationToken() throws Exception {
        Path tokenFile = dataDir.resolve("bootstrap-activation.token");
        String firstLine = Files.readAllLines(tokenFile).get(0);
        return firstLine.trim();
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
