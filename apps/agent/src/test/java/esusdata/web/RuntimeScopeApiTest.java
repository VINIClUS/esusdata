package esusdata.web;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ADR 0015: the reads a client uses to discover its scope at runtime — municipalities in
 * {@code GET /auth/me}, {@code GET /results/periods} and {@code GET /runs} — each honouring the
 * same scope rules as the routes they feed.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RuntimeScopeApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3304557";
    private static final String OTHER_MUNICIPALITY = "3550308";
    /** Kept apart from MUNICIPALITY: the context is shared, so published results would add jobs. */
    private static final String RUNS_MUNICIPALITY = "3106200";

    @Test
    void meListsOnlyMunicipalitiesWhoseAggregateTheUserMayRead() throws Exception {
        String user = createUser("scope-me-" + System.nanoTime());
        grantMunicipality(user, Role.AUDITOR, "3541307");
        grantMunicipality(user, Role.MANAGER, MUNICIPALITY);
        grantTeam(user, Role.TEAM_SCOPED_PROFESSIONAL, OTHER_MUNICIPALITY, "2750325", null);

        HttpResponse<String> me = get(user, "/api/v1/auth/me");

        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"municipalities\":[\"3304557\",\"3541307\"]");
    }

    @Test
    void periodsAreDistinctNewestFirstAndScoped() throws Exception {
        String manager = createUser("scope-periods-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        publish(manager, "2026-04");
        publish(manager, "2026-06");
        publish(manager, "2026-06");

        HttpResponse<String> periods = get(manager, "/api/v1/results/periods?municipalityIbge=" + MUNICIPALITY);
        HttpResponse<String> outOfScope =
                get(manager, "/api/v1/results/periods?municipalityIbge=" + OTHER_MUNICIPALITY);

        assertThat(periods.statusCode()).isEqualTo(200);
        assertThat(periods.body()).isEqualTo("[\"2026-06\",\"2026-04\"]");
        assertThat(outOfScope.statusCode()).isEqualTo(404);
    }

    @Test
    void runsListNewestFirstUpToTheLimitAndScoped() throws Exception {
        String manager = createUser("scope-runs-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, RUNS_MUNICIPALITY);
        insertJob("job-scope-old", RUNS_MUNICIPALITY, Instant.parse("2026-09-01T10:00:00Z"));
        insertJob("job-scope-new", RUNS_MUNICIPALITY, Instant.parse("2026-09-02T10:00:00Z"));
        insertJob("job-scope-other", OTHER_MUNICIPALITY, Instant.parse("2026-09-03T10:00:00Z"));

        HttpResponse<String> latest = get(manager, "/api/v1/runs?municipalityIbge=" + RUNS_MUNICIPALITY + "&limit=1");
        HttpResponse<String> all = get(manager, "/api/v1/runs?municipalityIbge=" + RUNS_MUNICIPALITY);

        assertThat(latest.statusCode()).isEqualTo(200);
        assertThat(latest.body()).contains("job-scope-new").doesNotContain("job-scope-old");
        assertThat(all.body().indexOf("job-scope-new")).isLessThan(all.body().indexOf("job-scope-old"));
        assertThat(all.body()).doesNotContain("job-scope-other");
    }

    @Test
    void runsListRejectsOutOfScopeAndOutOfRangeRequests() throws Exception {
        String auditor = createUser("scope-runs-auditor-" + System.nanoTime());
        grantMunicipality(auditor, Role.AUDITOR, RUNS_MUNICIPALITY);
        String manager = createUser("scope-runs-limit-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, RUNS_MUNICIPALITY);

        assertThat(get(auditor, "/api/v1/runs?municipalityIbge=" + RUNS_MUNICIPALITY)
                        .statusCode())
                .isEqualTo(404);
        assertThat(get(manager, "/api/v1/runs?municipalityIbge=" + RUNS_MUNICIPALITY + "&limit=0")
                        .statusCode())
                .isEqualTo(400);
        assertThat(get(manager, "/api/v1/runs?municipalityIbge=" + RUNS_MUNICIPALITY + "&limit=101")
                        .statusCode())
                .isEqualTo(400);
    }

    private void publish(String manager, String referencePeriod) throws Exception {
        publishResult(
                manager,
                MUNICIPALITY,
                referencePeriod,
                new IndicatorResult(
                        IndicatorResult.IndicatorStatus.COMPUTED,
                        "60.0000",
                        BigInteger.valueOf(3),
                        BigInteger.valueOf(5),
                        "PROGRAMADOS_MAIS_ESPONTANEOS",
                        Classification.BOM,
                        referencePeriod,
                        "c1-mais-acesso@0.1.0",
                        referencePeriod + "-28",
                        MUNICIPALITY,
                        List.of(),
                        "c1-exact-ratio@1"),
                List.of());
    }

    private void insertJob(String jobId, String municipalityIbge, Instant createdAt) {
        jdbc.update(
                """
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, execution_generation, created_at)
                VALUES (?,?,?,?,?,?, 'QUEUED', 0, 3, 0, ?)
                """,
                jobId,
                "run-" + jobId,
                municipalityIbge,
                "c1-mais-acesso",
                "c1-mais-acesso@0.1.0",
                "2026-08",
                createdAt.toString());
    }

    private HttpResponse<String> get(String userId, String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + path))
                                .header("Cookie", sessionCookie(userId))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
