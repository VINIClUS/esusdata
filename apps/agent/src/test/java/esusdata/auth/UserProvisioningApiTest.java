package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.web.ApiFixtureSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The other half of the plan's Fatia B destravamento: {@code POST /api/v1/users}, and the claim
 * in {@link esusdata.auth.UserProvisioning}'s javadoc that the returned
 * token consumes through the exact same {@code BootstrapActivation.activate} route the very first
 * admin uses — proven here by actually calling {@code POST /api/v1/auth/activate} with it, not by
 * inspecting the source.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UserProvisioningApiTest extends ApiFixtureSupport {

    @Test
    void anAdminCanProvisionAUserAndTheReturnedTokenActivatesItThroughTheRealRoute() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);
        String username = "gestor-" + System.nanoTime();

        HttpResponse<String> created = authenticatedPost(
                cookie,
                URI.create(BASE_URL + "/api/v1/users"),
                "{\"username\":\"" + username + "\",\"displayName\":\"Gestora de Teste\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        String activationToken = extractField(created.body(), "activationToken");
        assertThat(activationToken).isNotBlank();

        HttpResponse<String> duplicate = authenticatedPost(
                cookie,
                URI.create(BASE_URL + "/api/v1/users"),
                "{\"username\":\"" + username + "\",\"displayName\":\"Outra Pessoa\"}");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("USERNAME_ALREADY_EXISTS");

        String csrfToken = csrfTokenViaReady();
        HttpResponse<String> activate = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/activate"))
                                .header("Content-Type", "application/json")
                                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                                .header("X-XSRF-TOKEN", csrfToken)
                                .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"" + activationToken
                                        + "\",\"password\":\"a-strong-enough-passphrase-2\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(activate.statusCode()).isEqualTo(204);
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static String csrfTokenViaReady() throws Exception {
        HttpResponse<String> ready = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        for (String setCookie : ready.headers().allValues("Set-Cookie")) {
            if (setCookie.startsWith("XSRF-TOKEN=")) {
                String rest = setCookie.substring("XSRF-TOKEN=".length());
                int semicolon = rest.indexOf(';');
                return semicolon < 0 ? rest : rest.substring(0, semicolon);
            }
        }
        throw new IllegalStateException("no XSRF-TOKEN cookie was issued");
    }
}
