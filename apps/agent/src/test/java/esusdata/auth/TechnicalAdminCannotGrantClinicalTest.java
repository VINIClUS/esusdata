package esusdata.auth;

import esusdata.auth.model.Grant;
import esusdata.auth.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import esusdata.web.ApiFixtureSupport;

import esusdata.source.SourceController;
/**
 * ENG-45 at the HTTP level. {@code role_permissions} (V3 seed) structurally never gives
 * TECHNICAL_ADMIN {@code read_clinical} — no row exists to grant. This pins the other half:
 * {@link esusdata.auth.AccessAdministrationService} refuses EVERY
 * self-targeted grant outright, so a technical admin cannot even attempt to route around the
 * missing row by granting themselves a clinical role.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TechnicalAdminCannotGrantClinicalTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void technicalAdminCannotGrantAnyRoleToItself() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/users/" + admin + "/grants"),
                "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\""
                        + MUNICIPALITY + "\"}");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("SELF_GRANT_FORBIDDEN");
    }

    /**
     * {@code block} does not revoke the actor's own grant row, so
     * {@code BootstrapActivation.ensureBootstrapAdmin}'s idempotency check would keep seeing an
     * active TECHNICAL_ADMIN grant after a self-block and never mint a replacement — a sole admin
     * blocking themselves would brick the installation with no recovery route in this recorte.
     */
    @Test
    void technicalAdminCannotBlockItself() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/users/" + admin + "/block"), null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("SELF_BLOCK_FORBIDDEN");
    }

    /**
     * Revoking (unlike blocking) DOES clear {@code revoked_at}, which
     * {@code BootstrapActivation.ensureBootstrapAdmin}'s idempotency check honors — a sole admin
     * self-revoking their TECHNICAL_ADMIN grant would make the next boot insert a second
     * {@code username = 'admin'} row into a UNIQUE column, crashing startup.
     */
    @Test
    void technicalAdminCannotRevokeItsOwnGrant() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);
        List<Grant> grants = grantRepository.activeGrantsForUser(admin);
        String grantId = grants.get(0).grantId();

        HttpResponse<String> response = authenticatedDelete(cookie,
                URI.create(BASE_URL + "/api/v1/users/" + admin + "/grants/" + grantId));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("SELF_GRANT_FORBIDDEN");
    }

    /**
     * {@code manage_access} is installation-eligible ({@code ScopeResolver.isInstallationEligible})
     * but {@code ApiAuthorization.requireInstallationPermission} accepts ONLY an {@code
     * INSTALLATION}-scoped grant — never a {@code MUNICIPALITY}-scoped one, even though that same
     * shape works for {@code manage_source} on {@code SourceController} (a different route with a
     * genuine municipality of its own). Getting this inverted would let any authenticated caller
     * grant themselves {@code read_clinical} through {@code POST /users/{id}/grants}.
     */
    @Test
    void aCallerWithoutInstallationScopedManageAccessIsRefusedTheSame404() throws Exception {
        String noGrants = createUser("no-grants-" + System.nanoTime());
        HttpResponse<String> withoutAnyGrant = authenticatedPost(reauthenticatedSessionCookie(noGrants),
                URI.create(BASE_URL + "/api/v1/users"),
                "{\"username\":\"attempt-1-" + System.nanoTime() + "\",\"displayName\":\"x\"}");
        assertThat(withoutAnyGrant.statusCode()).isEqualTo(404);

        String municipalAdmin = createUser("municipal-admin-" + System.nanoTime());
        grantMunicipality(municipalAdmin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        HttpResponse<String> withMunicipalGrant = authenticatedPost(
                reauthenticatedSessionCookie(municipalAdmin),
                URI.create(BASE_URL + "/api/v1/users"),
                "{\"username\":\"attempt-2-" + System.nanoTime() + "\",\"displayName\":\"x\"}");
        assertThat(withMunicipalGrant.statusCode()).isEqualTo(404);
    }

    @Test
    void technicalAdminCanGrantAManagerRoleToAnotherUserButNeverGainsReadClinicalItself() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);
        String target = createUser("manager-" + System.nanoTime());

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/users/" + target + "/grants"),
                "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\""
                        + MUNICIPALITY + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("MANAGER");

        HttpResponse<String> adminReadsResults = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/results?municipalityIbge="
                                + MUNICIPALITY + "&indicatorPack=c1-mais-acesso&referencePeriod=2026-03"))
                        .header("Cookie", sessionCookie(admin)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(adminReadsResults.statusCode()).isEqualTo(404);
    }
}
