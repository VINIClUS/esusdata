package esusdata.report;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ADR 0024 over real HTTP: who may export, what the file holds, and that every download checks
 * the scope again.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportExportApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3170206";
    private static final String OTHER_MUNICIPALITY = "3550308";
    private static final URI EXPORTS = URI.create(BASE_URL + "/api/v1/exports");
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");

    @Test
    void aManagerExportsTheNewestPublishedResultOfEachCompetencia() throws Exception {
        String manager = createUser("export-manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        publish(manager, "2026-02", "40.0000");
        publish(manager, "2026-03", "50.0000");
        publish(manager, "2026-03", "60.0000");
        publish(manager, "2026-05", "70.0000");

        HttpResponse<String> created = authenticatedPost(
                sessionCookie(manager),
                EXPORTS,
                "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-04\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body())
                .contains("\"rowCount\":2", "\"format\":\"CSV\"", "\"indicatorPack\":null")
                .contains("\"fileName\":\"esusdata-" + MUNICIPALITY + "-todos-2026-01_2026-04.csv\"");
        String id = idOf(created.body());

        HttpResponse<String> list = get(manager, "/api/v1/exports?municipalityIbge=" + MUNICIPALITY);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(id);

        HttpResponse<byte[]> download = download(manager, id, MUNICIPALITY);
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Type")).hasValue("text/csv;charset=UTF-8");
        assertThat(download.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(download.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"esusdata-" + MUNICIPALITY + "-todos-2026-01_2026-04.csv\"");
        String csv = new String(download.body(), StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains("\"2026-02\"", "\"40,0000\"", "\"60,0000\"").doesNotContain("50,0000", "70,0000");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM auth_audit WHERE target = ? AND event_type IN"
                                + " ('EXPORT_CREATED', 'EXPORT_DOWNLOADED')",
                        Integer.class,
                        id))
                .isEqualTo(2);
    }

    @Test
    void anotherMunicipalityOrATeamGrantNeverReachesTheExport() throws Exception {
        String manager = createUser("export-owner-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String id = idOf(authenticatedPost(
                        sessionCookie(manager),
                        EXPORTS,
                        "{\"municipalityIbge\":\"" + MUNICIPALITY
                                + "\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-01\"}")
                .body());

        String outsider = createUser("export-outsider-" + System.nanoTime());
        grantMunicipality(outsider, Role.MANAGER, OTHER_MUNICIPALITY);
        String team = createUser("export-team-" + System.nanoTime());
        grantTeam(team, Role.TEAM_SCOPED_PROFESSIONAL, MUNICIPALITY, "2750325", null);
        String admin = createUser("export-admin-" + System.nanoTime());
        grantInstallation(admin, Role.TECHNICAL_ADMIN);

        // Asking for the right municipality without the grant, and for its own municipality with
        // another's id, are the same 404.
        assertThat(download(outsider, id, MUNICIPALITY).statusCode()).isEqualTo(404);
        assertThat(download(outsider, id, OTHER_MUNICIPALITY).statusCode()).isEqualTo(404);
        assertThat(download(team, id, MUNICIPALITY).statusCode()).isEqualTo(404);
        assertThat(download(admin, id, MUNICIPALITY).statusCode()).isEqualTo(404);
        assertThat(get(team, "/api/v1/exports?municipalityIbge=" + MUNICIPALITY).statusCode())
                .isEqualTo(404);
        assertThat(authenticatedPost(
                                sessionCookie(admin),
                                EXPORTS,
                                "{\"municipalityIbge\":\"" + MUNICIPALITY
                                        + "\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-01\"}")
                        .statusCode())
                .isEqualTo(404);
    }

    @Test
    void refusesBadRangesAndExpiredExports() throws Exception {
        String manager = createUser("export-limits-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);

        HttpResponse<String> tooLong = authenticatedPost(
                sessionCookie(manager),
                EXPORTS,
                "{\"municipalityIbge\":\"" + MUNICIPALITY + "\",\"fromPeriod\":\"2024-01\",\"toPeriod\":\"2026-01\"}");
        HttpResponse<String> unknownPack = authenticatedPost(
                sessionCookie(manager),
                EXPORTS,
                "{\"municipalityIbge\":\"" + MUNICIPALITY
                        + "\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-01\",\"indicatorPack\":\"x\"}");
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(unknownPack.statusCode()).isEqualTo(400);

        String id = idOf(authenticatedPost(
                        sessionCookie(manager),
                        EXPORTS,
                        "{\"municipalityIbge\":\"" + MUNICIPALITY
                                + "\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-01\"}")
                .body());
        jdbc.update("UPDATE report_exports SET expires_at = '2000-01-01T00:00:00Z' WHERE export_id = ?", id);

        assertThat(download(manager, id, MUNICIPALITY).statusCode()).isEqualTo(404);
        assertThat(get(manager, "/api/v1/exports?municipalityIbge=" + MUNICIPALITY)
                        .body())
                .doesNotContain(id);
    }

    private void publish(String manager, String referencePeriod, String value) throws Exception {
        publishResult(
                manager,
                MUNICIPALITY,
                referencePeriod,
                new IndicatorResult(
                        IndicatorResult.IndicatorStatus.COMPUTED,
                        value,
                        BigInteger.valueOf(3),
                        BigInteger.valueOf(5),
                        "PROGRAMADOS_MAIS_ESPONTANEOS",
                        Classification.BOM,
                        referencePeriod,
                        "c1-mais-acesso@0.2.0",
                        referencePeriod + "-28",
                        MUNICIPALITY,
                        List.of(),
                        "c1-exact-ratio@1"),
                List.of());
    }

    private static String idOf(String body) {
        Matcher matcher = ID.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get(String userId, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + path))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<byte[]> download(String userId, String id, String municipalityIbge) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/exports/" + id + "/content?municipalityIbge="
                                    + municipalityIbge))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
