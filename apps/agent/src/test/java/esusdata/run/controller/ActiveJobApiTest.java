package esusdata.run.controller;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.web.ApiFixtureSupport;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR 0026 over HTTP: a second {@code POST /runs} for a competência whose live job is still
 * active — another tab, a double click, each with its own Idempotency-Key — gets 409 {@code
 * ACTIVE_JOB_EXISTS} naming the job to follow, and no second job exists. The worker polls once an
 * hour here, so the first job deterministically stays {@code QUEUED}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActiveJobApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Value("${observatorio.app.build}")
    private String appBuild;

    @DynamicPropertySource
    static void idleWorker(DynamicPropertyRegistry registry) {
        registry.add("observatorio.job-runner.poll-interval-ms", () -> "3600000");
    }

    @Test
    void aSecondRunForAnActiveCompetenciaIsRefusedWithTheActiveJobId() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String firstTab = sessionCookie(manager);
        String secondTab = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);

        HttpResponse<String> first =
                authenticatedPostWithIdempotency(firstTab, "idem-" + System.nanoTime(), liveRun(sourceId));
        HttpResponse<String> second =
                authenticatedPostWithIdempotency(secondTab, "idem-" + System.nanoTime(), liveRun(sourceId));

        assertThat(first.statusCode()).isEqualTo(202);
        String activeJobId = extractField(first.body(), "jobId");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("\"code\":\"ACTIVE_JOB_EXISTS\"");
        assertThat(extractField(second.body(), "jobId")).isEqualTo(activeJobId);
    }

    /** results.app_build carries the real version, never the "dev" fallback. */
    @Test
    void theBuildStampIsTheProjectVersion() {
        assertThat(appBuild).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static String liveRun(String sourceId) {
        return "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"indicatorPack\":\"" + C1Rule.INDICATOR_PACK
                + "\",\"ruleVersion\":\"" + C1Rule.RULE_VERSION + "\",\"referencePeriod\":\"2026-03\","
                + "\"sourceId\":\"" + sourceId + "\"}";
    }
}
