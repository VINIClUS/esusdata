package esusdata.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import esusdata.web.SecuritySliceTestSupport;
/** ENG-49: CSRF, Origin/Host validation, and the fixed security headers on every response. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CsrfAndOriginTest extends SecuritySliceTestSupport {

    @Test
    void readyResponseCarriesTheFixedSecurityHeaders() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        assertThat(response.headers().allValues("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
    }

    @Test
    void loginWithoutTheCsrfHeaderIsRejected() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        // Fetch the CSRF cookie first, deliberately never send it back as a header.
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"x\",\"password\":\"y\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void loginWithAMismatchedOriginIsRejected() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Origin", "http://evil.example.com")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"x\",\"password\":\"y\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("ORIGIN_NOT_ALLOWED");
    }

    @Test
    void theViteDevelopmentOriginIsAcceptedForStateChangingRequests() throws Exception {
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = csrfTokenFrom(cookieManager);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Origin", "http://localhost:5173")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"x\",\"password\":\"y\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void aRequestWithAnUnrecognizedHostHeaderIsRejected() throws Exception {
        String rawResponse;
        try (Socket socket = new Socket("127.0.0.1", PORT)) {
            OutputStream out = socket.getOutputStream();
            String request = "GET /api/v1/ready HTTP/1.1\r\n"
                    + "Host: attacker.example.com\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            rawResponse = sb.toString();
        }

        assertThat(rawResponse).contains("403");
        assertThat(rawResponse).contains("ORIGIN_NOT_ALLOWED");
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
}
