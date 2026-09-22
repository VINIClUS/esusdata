package br.gov.observatorioaps.access.adapter.in.http;

import br.gov.observatorioaps.access.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.testsupport.ApiFixtureSupport;

/**
 * §1.12.7 L541: granting, revoking, or blocking a user immediately bumps their
 * {@code authorization_version} and revokes their existing sessions — proven here through the
 * real {@code SessionService.validate} path an already-open {@code GET /auth/me} session hits on
 * its very next request, not by inspecting the {@code sessions} table directly.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class GrantMutationRevokesSessionsTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void grantingARoleRevokesTheTargetsExistingSession() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String adminCookie = reauthenticatedSessionCookie(admin);

        String target = createUser("target-" + System.nanoTime());
        String targetCookie = sessionCookie(target);
        assertThat(meStatus(targetCookie)).isEqualTo(200);

        HttpResponse<String> grant = authenticatedPost(adminCookie,
                URI.create(BASE_URL + "/api/v1/users/" + target + "/grants"),
                "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\""
                        + MUNICIPALITY + "\"}");
        assertThat(grant.statusCode()).isEqualTo(201);

        assertThat(meStatus(targetCookie)).isEqualTo(401);
    }

    @Test
    void revokingAGrantRevokesTheTargetsExistingSession() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);

        String target = createUser("target-" + System.nanoTime());
        HttpResponse<String> grant = authenticatedPost(reauthenticatedSessionCookie(admin),
                URI.create(BASE_URL + "/api/v1/users/" + target + "/grants"),
                "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\""
                        + MUNICIPALITY + "\"}");
        assertThat(grant.statusCode()).isEqualTo(201);
        String grantId = extractField(grant.body(), "grantId");

        String targetCookie = sessionCookie(target);
        assertThat(meStatus(targetCookie)).isEqualTo(200);

        HttpResponse<String> revoke = authenticatedDelete(reauthenticatedSessionCookie(admin),
                URI.create(BASE_URL + "/api/v1/users/" + target + "/grants/" + grantId));
        assertThat(revoke.statusCode()).isEqualTo(204);

        assertThat(meStatus(targetCookie)).isEqualTo(401);
    }

    @Test
    void blockingAUserRevokesTheirExistingSession() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);

        String target = createUser("target-" + System.nanoTime());
        String targetCookie = sessionCookie(target);
        assertThat(meStatus(targetCookie)).isEqualTo(200);

        HttpResponse<String> block = authenticatedPost(reauthenticatedSessionCookie(admin),
                URI.create(BASE_URL + "/api/v1/users/" + target + "/block"), null);
        assertThat(block.statusCode()).isEqualTo(204);

        assertThat(meStatus(targetCookie)).isEqualTo(401);
    }

    private int meStatus(String cookie) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/auth/me"))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
