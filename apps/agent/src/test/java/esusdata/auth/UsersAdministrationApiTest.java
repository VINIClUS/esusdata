package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.auth.model.UserState;
import esusdata.web.ApiFixtureSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;

/** The users screen's API: every account with its active grants, and undoing a block. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UsersAdministrationApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void theAdminListsEveryAccountWithItsActiveGrantsAndNoPasswordMaterial() throws Exception {
        String admin = admin();
        String manager = createUser("mgr-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);

        HttpResponse<String> response = get(admin, "/api/v1/users");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"userId\":\"" + manager + "\"")
                .contains("\"state\":\"ACTIVE\"")
                .contains("\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\"" + MUNICIPALITY
                        + "\"")
                .doesNotContain("password")
                .doesNotContain("UNSET");
    }

    @Test
    void onlyWhoManagesAccessListsTheUsers() throws Exception {
        String manager = createUser("mgr-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);

        // Like every installation-scoped route: the opaque 404, not a 403 that confirms it exists.
        assertThat(get(manager, "/api/v1/users").statusCode()).isEqualTo(404);
    }

    @Test
    void unblockingRestoresAnActivatedAccountAndNeedsAReauth() throws Exception {
        String admin = admin();
        String target = createUser("target-" + System.nanoTime());
        // An activated account: it has its own password (the fixture user does not).
        userRepository.setPassword(target, "argon2-hash", "ARGON2ID", "{}", "v1");
        URI block = URI.create(BASE_URL + "/api/v1/users/" + target + "/block");
        URI unblock = URI.create(BASE_URL + "/api/v1/users/" + target + "/unblock");

        assertThat(authenticatedPost(reauthenticatedSessionCookie(admin), block, null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(userRepository.findById(target).orElseThrow().state()).isEqualTo(UserState.BLOCKED);
        assertThat(authenticatedPost(sessionCookie(admin), unblock, null).statusCode())
                .isEqualTo(401);

        assertThat(authenticatedPost(reauthenticatedSessionCookie(admin), unblock, null)
                        .statusCode())
                .isEqualTo(204);
        assertThat(userRepository.findById(target).orElseThrow().state()).isEqualTo(UserState.ACTIVE);
        // Not blocked any more: a second unblock is a bad request, not a silent success.
        assertThat(authenticatedPost(reauthenticatedSessionCookie(admin), unblock, null)
                        .statusCode())
                .isEqualTo(400);
    }

    @Test
    void anAccountThatNeverActivatedGoesBackToPendingActivationNotActive() throws Exception {
        String admin = admin();
        String cookie = reauthenticatedSessionCookie(admin);
        HttpResponse<String> created = authenticatedPost(
                cookie,
                URI.create(BASE_URL + "/api/v1/users"),
                "{\"username\":\"novo-" + System.nanoTime() + "\",\"displayName\":\"Novo\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        String userId =
                new ObjectMapper().readTree(created.body()).get("userId").asString();

        authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/users/" + userId + "/block"), null);
        authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/users/" + userId + "/unblock"), null);

        assertThat(userRepository.findById(userId).orElseThrow().state()).isEqualTo(UserState.PENDING_ACTIVATION);
    }

    private String admin() {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        return admin;
    }

    private HttpResponse<String> get(String userId, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + path))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
