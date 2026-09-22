package br.gov.observatorioaps.execution.adapter.in.http;

import br.gov.observatorioaps.access.domain.Role;
import br.gov.observatorioaps.indicators.packs.c1.C1Rule;
import br.gov.observatorioaps.execution.application.CancellationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.testsupport.ApiFixtureSupport;

/**
 * ENG-07/ENG-23 "pela rota HTTP": {@code CancellationTokenTest} proves the token itself;
 * {@code LiveAcquisitionEndToEndTest} proves a pre-set token aborts a real PostgreSQL acquisition
 * with no staging/publication. The one hop neither proves is HTTP → {@link CancellationRegistry}
 * — this test proves exactly that hop, deterministically: a job inserted directly as {@code
 * RUNNING} (the real worker's {@code acquireNext} only ever touches {@code QUEUED} rows, so it
 * cannot interfere) and registered in the SAME registry {@code JobWorker.processJob} uses in
 * production. Nothing here owns finalizing {@code CANCEL_REQUESTED} into {@code CANCELLED} — that
 * is {@code JobWorker.finalizeCancellationIfOwned}'s job, proven elsewhere — so this test stops at
 * the CAS and the registry flag, not a terminal state.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RunCancelHttpTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";
    private static final String OTHER_MUNICIPALITY = "3550308";

    @Autowired
    CancellationRegistry cancellationRegistry;

    @Test
    void cancellingARunningJobOverHttpReachesTheCancellationRegistry() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'RUNNING', 1, 3, 'proc-test-owns-nothing', 1, ?, ?)
                """, jobId, "run-" + jobId, MUNICIPALITY, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", clock.instant().toString(), sourceId);
        cancellationRegistry.register(jobId);

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/cancel"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"state\":\"CANCEL_REQUESTED\"");
        assertThat(jdbc.queryForObject("select state from jobs where job_id = ?", String.class, jobId))
                .isEqualTo("CANCEL_REQUESTED");
        assertThat(cancellationRegistry.find(jobId).orElseThrow().isCancelRequested()).isTrue();
    }

    @Test
    void cancellingAnAlreadyCancelRequestedJobIsIdempotent() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id, cancel_requested_at)
                VALUES (?,?,?,?,?,?, 'CANCEL_REQUESTED', 1, 3, 'proc-test-owns-nothing', 1, ?, ?, ?)
                """, jobId, "run-" + jobId, MUNICIPALITY, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", clock.instant().toString(), sourceId, clock.instant().toString());

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/cancel"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"state\":\"CANCEL_REQUESTED\"");
    }

    @Test
    void cancellingATerminalJobIsRefusedWith409() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id, finished_at)
                VALUES (?,?,?,?,?,?, 'SUCCEEDED', 1, 3, 'proc-test-owns-nothing', 1, ?, ?, ?)
                """, jobId, "run-" + jobId, MUNICIPALITY, C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", clock.instant().toString(), sourceId, clock.instant().toString());

        HttpResponse<String> response = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/cancel"), null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("JOB_NOT_CANCELLABLE");
    }

    /**
     * {@code findAuthorized} — shared by {@code GET /runs/{id}} and cancel — is the one place a
     * forgotten scope check would open (or let a caller mutate) an object belonging to a
     * municipality they hold no grant for. Proven on both routes against the SAME out-of-scope
     * job: a denied read, and a denied cancel that provably left the row untouched.
     */
    @Test
    void aManagerInMunicipalityACannotReadOrCancelAJobBelongingToMunicipalityB() throws Exception {
        String manager = createUser("manager-a-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String cookie = sessionCookie(manager);

        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, OTHER_MUNICIPALITY);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'RUNNING', 1, 3, 'proc-test-owns-nothing', 1, ?, ?)
                """, jobId, "run-" + jobId, OTHER_MUNICIPALITY, C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION, "2026-03", clock.instant().toString(), sourceId);

        HttpResponse<String> read = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/runs/" + jobId))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(read.statusCode()).isEqualTo(404);
        assertThat(read.body()).contains("NOT_FOUND");

        HttpResponse<String> cancel = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/runs/" + jobId + "/cancel"), null);
        assertThat(cancel.statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select state from jobs where job_id = ?", String.class, jobId))
                .isEqualTo("RUNNING");
    }
}
