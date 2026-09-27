package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import esusdata.web.ApiFixtureSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * §1.10 / §1.12.7 L550: source registration and its diagnostic. This Spring test slice runs with
 * no {@code observatorio.source.allowed-destinations} configured — "a fresh install authorizes no
 * destination until an operator configures one" ({@code RunConfig}) — so every destination
 * is refused by construction here. The CONNECTED outcome needs a real reachable PostgreSQL on an
 * allowlisted destination (Testcontainers, as {@code LiveAcquisitionEndToEndTest} does) and is
 * explicitly NOT exercised by this class; only the refusal paths are.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";
    private static final String MUNICIPALITY_B = "3304557";

    @Test
    void creatingASourceReturnsTheSecretReferenceNeverASecretValue() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();

        HttpResponse<String> response =
                authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources"), createSourceJson(sourceId));

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

        HttpResponse<String> response =
                authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("DESTINATION_NOT_ALLOWED");
        assertThat(response.body()).doesNotContain("PEC_DB_PASSWORD");
    }

    @Test
    void sourceCreationRequiresRecentReauthentication() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = sessionCookie(admin);

        HttpResponse<String> response = authenticatedPost(
                cookie, URI.create(BASE_URL + "/api/v1/sources"), createSourceJson("src-" + System.nanoTime()));

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

        HttpResponse<String> response = authenticatedPost(
                cookie, URI.create(BASE_URL + "/api/v1/sources"), createSourceJson(sourceId, MUNICIPALITY));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain(sourceId).doesNotContain(MUNICIPALITY_B);
    }

    @Test
    void aCallerWithoutManageSourceInThatMunicipalityIsRefusedTheSame404() throws Exception {
        String outsider = createUser("outsider-" + System.nanoTime());
        String cookie = reauthenticatedSessionCookie(outsider);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> response =
                authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void anInstallationAdminListsTheSourcesOfEveryMunicipalityWithoutASecretValue() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String sourceA = "src-a-" + System.nanoTime();
        String sourceB = "src-b-" + System.nanoTime();
        registerSource(sourceA, MUNICIPALITY);
        registerSource(sourceB, MUNICIPALITY_B);

        HttpResponse<String> response = get(sessionCookie(admin), "/api/v1/sources");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains(sourceA)
                .contains(sourceB)
                .contains("\"secretRef\":\"PEC_DB_PASSWORD\"")
                .doesNotContain("secretValue");
    }

    @Test
    void aMunicipalAdminListsOnlyTheSourcesOfItsOwnMunicipality() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String ownSource = "src-own-" + System.nanoTime();
        String otherSource = "src-other-" + System.nanoTime();
        registerSource(ownSource, MUNICIPALITY);
        registerSource(otherSource, MUNICIPALITY_B);

        HttpResponse<String> response = get(sessionCookie(admin), "/api/v1/sources");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains(ownSource)
                .doesNotContain(otherSource)
                .doesNotContain(MUNICIPALITY_B);
    }

    @Test
    void aCallerWithoutManageSourceGetsAnEmptyListNotAnError() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        registerSource("src-" + System.nanoTime(), MUNICIPALITY);

        HttpResponse<String> response = get(sessionCookie(manager), "/api/v1/sources");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void aTestedSourceCarriesItsLastDiagnosticAndItsRequirements() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        assertThat(get(cookie, "/api/v1/sources").body())
                .contains("\"id\":\"" + sourceId + "\"")
                .contains("\"lastDiagnostic\":null");
        authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        assertThat(get(cookie, "/api/v1/sources").body())
                .contains("\"lastDiagnostic\":{\"outcome\":\"DESTINATION_NOT_ALLOWED\"");
        HttpResponse<String> requirements = get(cookie, "/api/v1/sources/" + sourceId + "/requirements");
        assertThat(requirements.statusCode()).isEqualTo(200);
        assertThat(requirements.body())
                .isEqualTo("[{\"code\":\"READ_CONNECTION\",\"ok\":false},"
                        + "{\"code\":\"PEC_POSTGRESQL_FAMILY\",\"ok\":true},"
                        + "{\"code\":\"PEC_VERSION_IN_MATRIX\",\"ok\":true},"
                        + "{\"code\":\"MUNICIPAL_SCOPE\",\"ok\":true}]");
    }

    @Test
    void reRegisteringASourceHidesTheDiagnosticOfItsPreviousConfiguration() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/test"), null);

        authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/sources"), createSourceJson(sourceId));

        assertThat(get(cookie, "/api/v1/sources").body())
                .contains("\"id\":\"" + sourceId + "\",\"sourceConfigurationVersion\":2")
                .doesNotContain("DESTINATION_NOT_ALLOWED");
    }

    @Test
    void aLateDiagnosticOfAReplacedConfigurationNeverOverwritesTheCurrentOne() {
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        SourceRecord v1 = sourceRepository.findById(sourceId).orElseThrow();
        SourceRecord v2 = new SourceRecord(
                v1.id(),
                2,
                v1.sourceFamily(),
                v1.pecInstallationRole(),
                v1.sourceLocationKind(),
                v1.host(),
                v1.port(),
                v1.databaseName(),
                "outro_usuario",
                v1.secretRef(),
                v1.municipalityIbge(),
                v1.pecVersion(),
                v1.readModel(),
                v1.createdAt());
        sourceRepository.upsert(v2);

        sourceRepository.recordDiagnostic(sourceId, 2, "DESTINATION_NOT_ALLOWED", "refused", "2026-09-27T12:00:00Z");
        sourceRepository.recordDiagnostic(sourceId, 1, "CONNECTED", null, "2026-09-27T12:00:01Z");

        LastDiagnostic stored = sourceRepository.findLastDiagnostic(sourceId).orElseThrow();
        assertThat(stored.sourceConfigurationVersion()).isEqualTo(2);
        assertThat(stored.outcome()).isEqualTo("DESTINATION_NOT_ALLOWED");
        assertThat(stored.appliesTo(v2)).isTrue();
        assertThat(stored.appliesTo(v1)).isFalse();
    }

    @Test
    void anIsolationCheckOfAnUnreachableSourceIsStoredForItsCompetencia() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        assertThat(get(cookie, "/api/v1/sources").body()).contains("\"lastIsolationCheck\":null");

        HttpResponse<String> response = isolationCheck(cookie, sourceId, "{\"referencePeriod\":\"2026-03\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"referencePeriod\":\"2026-03\"")
                .contains("\"outcome\":\"DESTINATION_NOT_ALLOWED\"")
                .contains("\"registeredCount\":null")
                .doesNotContain("PEC_DB_PASSWORD");
        assertThat(get(cookie, "/api/v1/sources").body())
                .contains("\"lastIsolationCheck\":{\"referencePeriod\":\"2026-03\","
                        + "\"outcome\":\"DESTINATION_NOT_ALLOWED\"");
    }

    @Test
    void anIsolationCheckNeedsAValidCompetencia() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        assertThat(isolationCheck(cookie, sourceId, "{\"referencePeriod\":\"03/2026\"}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(isolationCheck(cookie, sourceId, "{}").statusCode()).isEqualTo(400);
    }

    @Test
    void anIsolationCheckOfANonPecSourceIsABadRequestNotAnError() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String sourceId = "src-" + System.nanoTime();
        authenticatedPost(
                cookie,
                URI.create(BASE_URL + "/api/v1/sources"),
                createSourceJson(sourceId).replace("PEC_POSTGRESQL", "EXTERNAL_DATASET"));

        HttpResponse<String> response = isolationCheck(cookie, sourceId, "{\"referencePeriod\":\"2026-03\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("not a PEC source");
    }

    @Test
    void anIsolationCheckRequiresRecentReauthentication() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> response =
                isolationCheck(sessionCookie(admin), sourceId, "{\"referencePeriod\":\"2026-03\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("REAUTHENTICATION_REQUIRED");
    }

    @Test
    void anIsolationCheckOfAnotherMunicipalitysSourceIsAnOpaque404() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = reauthenticatedSessionCookie(admin);
        String otherSource = "src-other-" + System.nanoTime();
        registerSource(otherSource, MUNICIPALITY_B);

        HttpResponse<String> response = isolationCheck(cookie, otherSource, "{\"referencePeriod\":\"2026-03\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain(otherSource);
    }

    @Test
    void aLateIsolationCheckOfAReplacedConfigurationNeverOverwritesTheCurrentOne() {
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        SourceRecord v1 = sourceRepository.findById(sourceId).orElseThrow();
        sourceRepository.upsert(new SourceRecord(
                v1.id(),
                2,
                v1.sourceFamily(),
                v1.pecInstallationRole(),
                v1.sourceLocationKind(),
                v1.host(),
                v1.port(),
                v1.databaseName(),
                "outro_usuario",
                v1.secretRef(),
                v1.municipalityIbge(),
                v1.pecVersion(),
                v1.readModel(),
                v1.createdAt()));

        sourceRepository.recordIsolationCheck(
                sourceId, new LastIsolationCheck(2, "2026-03", "CHECKED", 10_029L, 0L, 0, 0L, "2026-09-27T12:00:00Z"));
        sourceRepository.recordIsolationCheck(
                sourceId,
                new LastIsolationCheck(
                        1, "2026-02", "CONNECTION_FAILED", null, null, null, null, "2026-09-27T12:00:01Z"));

        LastIsolationCheck stored = sourceRepository.findLastIsolationChecks().get(sourceId);
        assertThat(stored.sourceConfigurationVersion()).isEqualTo(2);
        assertThat(stored.referencePeriod()).isEqualTo("2026-03");
        assertThat(stored.registeredCount()).isEqualTo(10_029L);
        assertThat(stored.otherMunicipalityCodes()).isZero();
    }

    @Test
    void requirementsOfAnotherMunicipalityOrAnUnknownSourceAreTheSameOpaque404() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String cookie = sessionCookie(admin);
        String otherSource = "src-other-" + System.nanoTime();
        registerSource(otherSource, MUNICIPALITY_B);

        HttpResponse<String> other = get(cookie, "/api/v1/sources/" + otherSource + "/requirements");
        HttpResponse<String> unknown =
                get(cookie, "/api/v1/sources/src-missing-" + System.nanoTime() + "/requirements");

        assertThat(other.statusCode()).isEqualTo(404);
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(other.body()).isEqualTo(unknown.body()).doesNotContain(otherSource);
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

    private HttpResponse<String> isolationCheck(String sessionCookie, String sourceId, String body) throws Exception {
        return authenticatedPost(
                sessionCookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/isolation-check"), body);
    }

    private static String createSourceJson(String sourceId) {
        return createSourceJson(sourceId, MUNICIPALITY);
    }

    private static String createSourceJson(String sourceId, String municipalityIbge) {
        return "{\"id\":\"" + sourceId + "\",\"sourceFamily\":\"PEC_POSTGRESQL\","
                + "\"pecInstallationRole\":\"PRONTUARIO\",\"sourceLocationKind\":\"PRIMARY\","
                + "\"host\":\"127.0.0.1\",\"port\":5432,\"databaseName\":\"esus\","
                + "\"dbUser\":\"esus_leitura\",\"secretRef\":\"PEC_DB_PASSWORD\","
                + "\"municipalityIbge\":\"" + municipalityIbge + "\",\"pecVersion\":\"5.4.37\","
                + "\"readModel\":\"PEC_DW\"}";
    }
}
