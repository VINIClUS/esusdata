package br.gov.observatorioaps.sources.adapter.in.http;

import br.gov.observatorioaps.access.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.testsupport.ApiFixtureSupport;

/**
 * §1.10 / §1.12.7 L550: source registration and its diagnostic. This Spring test slice runs with
 * no {@code observatorio.source.allowed-destinations} configured — "a fresh install authorizes no
 * destination until an operator configures one" ({@code JobRunnerConfig}) — so every destination
 * is refused by construction here. The CONNECTED outcome needs a real reachable PostgreSQL on an
 * allowlisted destination (Testcontainers, as {@code LiveAcquisitionEndToEndTest} does) and is
 * explicitly NOT exercised by this class; only the refusal paths are.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SourceApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";
    private static final String MUNICIPALITY_B = "3304557";

    @Test
    void creatingASourceReturnsTheSecretReferenceNeverASecretValue() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/sources"), createSourceJson(sourceId));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("PEC_DB_PASSWORD");
        assertThat(response.body()).doesNotContain("secretValue");
    }

    @Test
    void testingAnUnreachableSourceReportsDestinationNotAllowedWithoutRevealingTheSecret() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("DESTINATION_NOT_ALLOWED");
        assertThat(response.body()).doesNotContain("PEC_DB_PASSWORD");
    }

    @Test
    void sourceCreationRequiresRecentReauthentication() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = sessionCookie(admin);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/sources"), createSourceJson("src-" + System.nanoTime()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("REAUTHENTICATION_REQUIRED");
    }

    @Test
    void aSourceIdOwnedByAnotherMunicipalityIsAnOpaqueNotFound() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY_B);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/sources"), createSourceJson(sourceId, MUNICIPALITY));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain(sourceId).doesNotContain(MUNICIPALITY_B);
    }

    @Test
    void aCallerWithoutManageSourceInThatMunicipalityIsRefusedTheSame404() throws Exception {
        String outsider = createUser("outsider-" + System.nanoTime());
        String cookie = reauthenticatedSessionCookie(outsider);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private String createSourceJson(String sourceId) {
        return createSourceJson(sourceId, MUNICIPALITY);
    }

    private String createSourceJson(String sourceId, String municipalityIbge) {
        return "{\"id\":\"" + sourceId + "\",\"sourceFamily\":\"PEC_POSTGRESQL\","
                + "\"pecInstallationRole\":\"PRONTUARIO\",\"sourceLocationKind\":\"PRIMARY\","
                + "\"host\":\"127.0.0.1\",\"port\":5432,\"databaseName\":\"esus\","
                + "\"dbUser\":\"esus_leitura\",\"secretRef\":\"PEC_DB_PASSWORD\","
                + "\"municipalityIbge\":\"" + municipalityIbge + "\",\"pecVersion\":\"5.4.37\","
                + "\"readModel\":\"PEC_DW\"}";
    }
}
