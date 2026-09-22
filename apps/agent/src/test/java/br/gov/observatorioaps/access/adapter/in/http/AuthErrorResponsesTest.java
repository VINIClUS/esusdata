package br.gov.observatorioaps.access.adapter.in.http;

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
import br.gov.observatorioaps.testsupport.SecuritySliceTestSupport;

/**
 * PR-review regressions: a validation-rejected activation password must come back as a 4xx
 * client error, not an unhandled {@code WeakPasswordException} escaping as a 500; and a
 * throttled login's {@code Retry-After} must be a value HTTP actually defines (delta-seconds),
 * not {@code Instant#toString()}'s ISO-8601 text.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuthErrorResponsesTest extends SecuritySliceTestSupport {

    @Test
    void activatingWithAWeakPasswordReturns400NotAServerError() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);
        String activationToken = readActivationToken();

        HttpResponse<String> response = post(client, "/api/v1/auth/activate", csrfToken,
                "{\"token\":\"" + activationToken + "\",\"password\":\"too-short\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("WEAK_PASSWORD");
    }

    @Test
    void aThrottledLoginReturnsRetryAfterAsDeltaSeconds() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);
        String body = "{\"username\":\"no-such-user\",\"password\":\"wrong-password\"}";

        HttpResponse<String> throttled = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            throttled = post(client, "/api/v1/auth/login", csrfToken, body);
        }

        assertThat(throttled.statusCode()).isEqualTo(429);
        String retryAfter = throttled.headers().firstValue("Retry-After").orElseThrow();
        // Delta-seconds is a plain non-negative integer; an ISO-8601 instant would contain
        // "T"/"Z"/":" and fail this parse instead. Strictly positive: the throttle is by
        // definition still active when the 429 is returned, so a truncated-to-zero value (the
        // pre-fix bug) would tell an already-blocked client to retry immediately.
        assertThat(Long.parseLong(retryAfter)).isGreaterThan(0);
    }

    private HttpResponse<String> post(HttpClient client, String path, String csrfToken, String body)
            throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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
