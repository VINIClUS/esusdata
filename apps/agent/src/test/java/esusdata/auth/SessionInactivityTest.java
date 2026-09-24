package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.auth.security.SessionCookie;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.web.ApiFixtureSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ENG-44 (§1.12.7 L537): "polling... não conta" toward session inactivity. Proves BOTH directions
 * against the same session — not just that polling fails to advance it (a predicate that matched
 * nothing would also pass that half alone), but that an ordinary interactive call still does,
 * against the real {@code sessions.last_interactive_at} column via {@code SessionAuthenticationFilter}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SessionInactivityTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void pollingGetRunsDoesNotAdvanceInteractivityButGetMeDoes() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        String jobId = "job-" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO jobs (job_id, run_id, municipality_ibge, indicator_pack, rule_version,
                    reference_period, state, attempt, max_attempts, process_instance_id,
                    execution_generation, created_at, source_id)
                VALUES (?,?,?,?,?,?, 'QUEUED', 0, 3, NULL, 0, ?, ?)
                """,
                jobId,
                "run-" + jobId,
                MUNICIPALITY,
                C1Rule.INDICATOR_PACK,
                C1Rule.RULE_VERSION,
                "2026-03",
                clock.instant().toString(),
                sourceId);

        String rawToken = rawSessionToken(manager);
        String cookie = SessionCookie.NAME + "=" + rawToken;
        String sessionId = sha256Hex(rawToken);

        Instant afterLogin = lastInteractiveAt(sessionId);

        Thread.sleep(20);
        HttpResponse<String> poll = get(cookie, "/api/v1/runs/" + jobId);
        assertThat(poll.statusCode()).isEqualTo(200);
        assertThat(lastInteractiveAt(sessionId)).isEqualTo(afterLogin);

        Thread.sleep(20);
        HttpResponse<String> me = get(cookie, "/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(lastInteractiveAt(sessionId)).isAfter(afterLogin);
    }

    private Instant lastInteractiveAt(String sessionId) {
        String value = jdbc.queryForObject(
                "select last_interactive_at from sessions where session_id = ?", String.class, sessionId);
        return Instant.parse(value);
    }

    private static HttpResponse<String> get(String cookie, String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + path))
                                .header("Cookie", cookie)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
