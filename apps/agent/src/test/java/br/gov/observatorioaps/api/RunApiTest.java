package br.gov.observatorioaps.api;

import br.gov.observatorioaps.extractionstore.ExtractFixtures;
import br.gov.observatorioaps.extractionstore.ExtractionManifest;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.10: {@code POST /runs}, {@code GET /runs/{id}}, {@code POST /runs/{id}/cancel} against the
 * real running worker — a fast poll interval (not the 2000 ms production default) keeps these
 * tests from being needlessly slow without changing what they prove.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @DynamicPropertySource
    static void fastPoll(DynamicPropertyRegistry registry) {
        registry.add("observatorio.job-runner.poll-interval-ms", () -> "100");
    }

    @Test
    void aRunCreatedOverHttpIsPickedUpByTheRealWorkerAndPublishesABlockedResult() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        ExtractionManifest manifest = ExtractFixtures.write(
                dataDir.resolve("extracts"), "ext-" + System.nanoTime(), sourceId, MUNICIPALITY,
                "2026-03", 7, 3, 2);

        HttpResponse<String> created = authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/runs"),
                createRunJson(sourceId, manifest.extractionId(), "2026-03"));
        assertThat(created.statusCode()).isEqualTo(202);
        assertThat(created.headers().firstValue("Location")).isPresent();
        String jobId = extractField(created.body(), "jobId");

        String finalBody = pollUntilTerminal(cookie, jobId);
        assertThat(finalBody).contains("\"state\":\"SUCCEEDED\"");
        assertThat(finalBody).doesNotContain("\"resultId\":null");
    }

    @Test
    void repeatingTheSameIdempotencyKeyReturnsTheSameJobButAConflictingPayloadIsRefused() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        ExtractionManifest manifest = ExtractFixtures.write(
                dataDir.resolve("extracts"), "ext-" + System.nanoTime(), sourceId, MUNICIPALITY,
                "2026-03", 1, 0, 0);
        String idempotencyKey = "idem-" + System.nanoTime();

        HttpResponse<String> first = authenticatedPostWithIdempotency(cookie, idempotencyKey,
                createRunJson(sourceId, manifest.extractionId(), "2026-03"));
        assertThat(first.statusCode()).isEqualTo(202);
        String firstJobId = extractField(first.body(), "jobId");

        HttpResponse<String> repeat = authenticatedPostWithIdempotency(cookie, idempotencyKey,
                createRunJson(sourceId, manifest.extractionId(), "2026-03"));
        assertThat(repeat.statusCode()).isEqualTo(202);
        assertThat(extractField(repeat.body(), "jobId")).isEqualTo(firstJobId);

        HttpResponse<String> conflicting = authenticatedPostWithIdempotency(cookie, idempotencyKey,
                createRunJson(sourceId, manifest.extractionId(), "2026-04"));
        assertThat(conflicting.statusCode()).isEqualTo(409);
        assertThat(conflicting.body()).contains("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void aCallerWithoutRunIndicatorInThatMunicipalityIsRefused404() throws Exception {
        String outsider = createUser("outsider-" + System.nanoTime());
        String cookie = sessionCookie(outsider);

        HttpResponse<String> response = authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/runs"),
                createRunJson("src-does-not-matter", null, "2026-03"));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void missingRequiredFieldsAreRefusedWith400() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        HttpResponse<String> response = authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/runs"),
                "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"indicatorPack\":\"" + C1Rule.INDICATOR_PACK
                        + "\",\"ruleVersion\":\"" + C1Rule.RULE_VERSION + "\",\"referencePeriod\":\"2026-03\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("BAD_REQUEST");
    }

    @Test
    void anInvalidReferencePeriodIsRefusedWith400() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        HttpResponse<String> response = authenticatedPost(cookie, URI.create(BASE_URL + "/api/v1/runs"),
                createRunJson("src-x", null, "not-a-period"));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void cancellingAnUnknownRunIsRefused404() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/runs/does-not-exist/cancel"), null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private String pollUntilTerminal(String cookie, String jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        String body = null;
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs/" + jobId))
                            .header("Cookie", cookie).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            body = response.body();
            if (body.contains("\"state\":\"SUCCEEDED\"") || body.contains("\"state\":\"FAILED\"")
                    || body.contains("\"state\":\"CANCELLED\"")) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("job " + jobId + " did not reach a terminal state in time; last body: " + body);
    }

    private HttpResponse<String> authenticatedPostWithIdempotency(
            String cookie, String idempotencyKey, String jsonBody) throws Exception {
        String csrfToken = csrfTokenViaReady();
        return java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", cookie + "; XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrfTokenViaReady() throws Exception {
        HttpResponse<String> ready = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/ready")).GET().build(),
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

    private String createRunJson(String sourceId, String extractionId, String referencePeriod) {
        String extractionField = extractionId == null ? "" : ",\"extractionId\":\"" + extractionId + "\"";
        return "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"indicatorPack\":\"" + C1Rule.INDICATOR_PACK
                + "\",\"ruleVersion\":\"" + C1Rule.RULE_VERSION + "\",\"referencePeriod\":\"" + referencePeriod
                + "\",\"sourceId\":\"" + sourceId + "\"" + extractionField + "}";
    }

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
