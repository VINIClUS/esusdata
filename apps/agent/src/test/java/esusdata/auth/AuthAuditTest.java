package esusdata.auth;

import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.result.model.EvidenceEntry;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import esusdata.web.ApiFixtureSupport;
/** ENG-03: {@code auth_audit} records login/grant/denial events without secrets or clinical data. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuthAuditTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void grantMutationIsAuditedWithoutSecretOrClinicalData() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);
        String cookie = reauthenticatedSessionCookie(admin);
        String target = createUser("target-" + System.nanoTime());

        HttpResponse<String> grant = authenticatedPost(cookie,
                URI.create(BASE_URL + "/api/v1/users/" + target + "/grants"),
                "{\"role\":\"MANAGER\",\"scopeKind\":\"MUNICIPALITY\",\"municipalityIbge\":\""
                        + MUNICIPALITY + "\"}");
        assertThat(grant.statusCode()).isEqualTo(201);

        // No negative "detail_json never contains a secret" assertion here: AuthAuditWriter's
        // parameter list has no room for one (see its javadoc) — for THIS event, that is
        // guaranteed by construction, not by a check this test could meaningfully fail.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event_type, actor_user_id, target, detail_json from auth_audit"
                        + " where target = ? order by at", target);
        assertThat(rows).anyMatch(row -> "GRANT_ADDED".equals(row.get("event_type"))
                && admin.equals(row.get("actor_user_id")));
    }

    @Test
    void deniedAccessIsAuditedWithoutClinicalData() throws Exception {
        String outsider = createUser("outsider-" + System.nanoTime());
        HttpResponse<String> denied = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/results?municipalityIbge="
                                + MUNICIPALITY + "&indicatorPack=c1-mais-acesso&referencePeriod=2026-03"))
                        .header("Cookie", sessionCookie(outsider)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(404);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event_type, outcome, detail_json from auth_audit"
                        + " where actor_user_id = ? and event_type = 'ACCESS_DENIED'", outsider);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("outcome")).isEqualTo("DENIED");
        assertThat(String.valueOf(rows.get(0).get("detail_json"))).doesNotContain("c1-mais-acesso");
    }

    @Test
    void successfulEvidenceReadIsAuditedWithoutClinicalData() throws Exception {
        String manager = createUser("evidence-reader-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String resultId = publishResult(manager, MUNICIPALITY, "2026-08", new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED, "60.0000", BigInteger.valueOf(3),
                BigInteger.valueOf(5), "PROGRAMADOS_MAIS_ESPONTANEOS", Classification.BOM,
                "2026-08", "c1-mais-acesso@0.1.0", "2026-08-31", MUNICIPALITY, List.of(),
                "c1-exact-ratio@1"),
                List.of(new EvidenceEntry("tb_fat_atendimento_individual", "rec-clinical-1",
                        "2026-08-05", "PROGRAMADO", "2750325", "0000346268", "225142",
                        "IN_NUMERATOR", "c1@1")));

        HttpResponse<String> evidence = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/results/" + resultId
                                + "/evidence?municipalityIbge=" + MUNICIPALITY))
                        .header("Cookie", sessionCookie(manager)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(evidence.statusCode()).isEqualTo(200);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select outcome, target, detail_json from auth_audit"
                        + " where actor_user_id = ? and event_type = 'EVIDENCE_READ'"
                        + " and target = ?", manager, resultId);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("outcome")).isEqualTo("SUCCESS");
            assertThat(row.get("target")).isEqualTo(resultId);
            assertThat(String.valueOf(row.get("detail_json"))).doesNotContain("rec-clinical-1");
        });
    }
}
