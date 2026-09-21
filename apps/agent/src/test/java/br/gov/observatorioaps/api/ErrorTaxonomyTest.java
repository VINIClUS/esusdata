package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.10 L399: distinguishable failure classes surface through {@code GET /runs/{id}} as a named
 * {@code failureCode}, never collapsed into a generic error and never a fabricated success.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ErrorTaxonomyTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @DynamicPropertySource
    static void fastPoll(DynamicPropertyRegistry registry) {
        registry.add("observatorio.job-runner.poll-interval-ms", () -> "100");
    }

    @Test
    void aLiveRunAgainstAnUnconfiguredDestinationFailsAsDestinationNotAllowed() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        // No observatorio.source.allowed-destinations configured anywhere in this test slice —
        // every destination is refused by construction (JobRunnerConfig: "a fresh install
        // authorizes no destination").
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> created = authenticatedPostWithIdempotency(cookie, "idem-" + System.nanoTime(),
                "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"indicatorPack\":\"" + C1Rule.INDICATOR_PACK
                        + "\",\"ruleVersion\":\"" + C1Rule.RULE_VERSION + "\",\"referencePeriod\":\"2026-03\","
                        + "\"sourceId\":\"" + sourceId + "\"}");
        assertThat(created.statusCode()).isEqualTo(202);
        String jobId = extractField(created.body(), "jobId");

        String finalBody = pollUntilTerminal(cookie, jobId);
        assertThat(finalBody).contains("\"state\":\"FAILED\"");
        assertThat(finalBody).contains("\"failureCode\":\"DESTINATION_NOT_ALLOWED\"");
        assertThat(finalBody).contains("\"resultId\":null");
    }

    @Test
    void aRunRequestingAnUnsupportedIndicatorPackFailsAsInvalidRequest() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> created = authenticatedPostWithIdempotency(cookie, "idem-" + System.nanoTime(),
                "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"indicatorPack\":\"unknown-pack\","
                        + "\"ruleVersion\":\"unknown-pack@1\",\"referencePeriod\":\"2026-03\","
                        + "\"sourceId\":\"" + sourceId + "\"}");
        assertThat(created.statusCode()).isEqualTo(202);
        String jobId = extractField(created.body(), "jobId");

        String finalBody = pollUntilTerminal(cookie, jobId);
        assertThat(finalBody).contains("\"state\":\"FAILED\"");
        assertThat(finalBody).contains("\"failureCode\":\"INVALID_REQUEST\"");
        assertThat(finalBody).contains("\"resultId\":null");
    }

    private String pollUntilTerminal(String cookie, String jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        String body = null;
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs/" + jobId))
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

    private String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
