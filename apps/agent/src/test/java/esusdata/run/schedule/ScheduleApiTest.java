package esusdata.run.schedule;

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
 * ADR 0028 over HTTP: the manager (RUN_INDICATOR) and the technical admin (MANAGE_SOURCE) both see
 * and drive a source's scheduler; anyone else gets the opaque 404; the mutations need a reauth.
 * No destination is allowed in this context, so "Verificar agora" ends in a recorded
 * COVERAGE_FAILED — the real path, without a PEC.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduleApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";
    private static final String OTHER_MUNICIPALITY = "3550308";

    @Test
    void theManagerSeesTheMunicipalitysSourcesWithTheirScheduler() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> response =
                get(sessionCookie(manager), "/api/v1/run-sources?municipalityIbge=" + MUNICIPALITY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"sourceId\":\"" + sourceId + "\"")
                .contains("\"coverageOutcome\":null")
                .contains("\"schedulerEnabled\":false")
                .contains("\"enabled\":true")
                .doesNotContain("PEC_DB_PASSWORD");
    }

    @Test
    void someoneWithoutEitherPermissionGetsTheOpaque404() throws Exception {
        String outsider = createUser("outsider-" + System.nanoTime());
        grantMunicipality(outsider, Role.MANAGER, OTHER_MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        assertThat(get(sessionCookie(outsider), "/api/v1/run-sources?municipalityIbge=" + MUNICIPALITY)
                        .statusCode())
                .isEqualTo(404);
        assertThat(runNow(reauthenticatedSessionCookie(outsider), sourceId).statusCode())
                .isEqualTo(404);
    }

    @Test
    void theAdminSwitchesTheSourceOffAfterAReauth() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        assertThat(putSchedule(sessionCookie(admin), sourceId, false).statusCode())
                .isEqualTo(401);
        HttpResponse<String> off = putSchedule(reauthenticatedSessionCookie(admin), sourceId, false);

        assertThat(off.statusCode()).isEqualTo(200);
        assertThat(off.body()).contains("\"enabled\":false");
        HttpResponse<String> tick = runNow(reauthenticatedSessionCookie(admin), sourceId);
        assertThat(tick.body()).contains("\"lastOutcome\":\"DISABLED\"");
    }

    @Test
    void verificarAgoraRefreshesTheCoverageAndRecordsWhatItConcluded() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> tick = runNow(reauthenticatedSessionCookie(manager), sourceId);

        assertThat(tick.statusCode()).isEqualTo(200);
        assertThat(tick.body())
                .contains("\"lastOutcome\":\"COVERAGE_FAILED\"")
                .contains("\"lastDetail\":\"DESTINATION_NOT_ALLOWED\"");
        assertThat(get(sessionCookie(manager), "/api/v1/run-sources?municipalityIbge=" + MUNICIPALITY)
                        .body())
                .contains("\"coverageOutcome\":\"DESTINATION_NOT_ALLOWED\"");
    }

    private HttpResponse<String> runNow(String cookie, String sourceId) throws Exception {
        return authenticatedPost(
                cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/schedule/run-now"), "{}");
    }

    private HttpResponse<String> putSchedule(String cookie, String sourceId, boolean enabled) throws Exception {
        return authenticatedPut(
                cookie,
                URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/schedule"),
                "{\"enabled\":" + enabled + "}");
    }

    private static HttpResponse<String> get(String sessionCookie, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + path))
                            .header("Cookie", sessionCookie)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
