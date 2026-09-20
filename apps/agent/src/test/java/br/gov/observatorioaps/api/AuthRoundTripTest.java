package br.gov.observatorioaps.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end proof this slice exists for: activate the bootstrap admin, log in, make an
 * authenticated call, log out, and confirm the session is actually gone afterward — all through
 * real HTTP (cookies, CSRF header, Origin/Host validation), not MockMvc's simulated request.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRoundTripTest extends SecuritySliceTestSupport {

    @Test
    void activateLoginCallMeAndLogoutEndToEnd() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        // Bootstrap the CSRF cookie (also proves /ready doubles as the SPA's first request).
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);

        String activationToken = readActivationToken();
        String password = "a-strong-enough-passphrase-1";

        HttpResponse<String> activate = post(client, "/api/v1/auth/activate", csrfToken,
                "{\"token\":\"" + activationToken + "\",\"password\":\"" + password + "\"}");
        assertThat(activate.statusCode()).isEqualTo(204);

        HttpResponse<String> login = post(client, "/api/v1/auth/login", csrfToken,
                "{\"username\":\"admin\",\"password\":\"" + password + "\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("OBS_SESSION=") && cookie.contains("HttpOnly"));

        HttpResponse<String> me = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("userId");
        // Regression guard: an authenticated request must never delete the CSRF cookie it already
        // issued — SessionManagementFilter's CsrfAuthenticationStrategy rotation firing on every
        // request (not just login) is exactly the bug this slice hunted down.
        assertThat(me.headers().allValues("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")
                        && (cookie.contains("Max-Age=0") || cookie.contains("1970")));

        HttpResponse<String> logout = post(client, "/api/v1/auth/logout", csrfToken, null);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> meAfterLogout = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(HttpClient client, String path, String csrfToken, String body)
            throws Exception {
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
}
