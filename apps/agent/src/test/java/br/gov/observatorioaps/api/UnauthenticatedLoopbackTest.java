package br.gov.observatorioaps.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-49: "loopback não é exceção para autenticação/autorização" — every protected route on
 * 127.0.0.1 still requires a valid session; there is no bypass for local traffic.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnauthenticatedLoopbackTest extends SecuritySliceTestSupport {

    @Test
    void meIsRejectedWithoutASession() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void logoutIsRejectedWithoutASession() throws Exception {
        java.net.CookieManager cookieManager = new java.net.CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = null;
        for (java.net.HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
                csrfToken = cookie.getValue();
            }
        }

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/logout"))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void readyRemainsPublicByDesign() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
