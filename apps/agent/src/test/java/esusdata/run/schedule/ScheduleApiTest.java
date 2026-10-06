package esusdata.run.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceRecord;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
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

    @Test
    void theStoredCoverageListsItsCompetenciasForTheExecutionScreen() throws Exception {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        sourceRepository.recordCoverage(
                sourceId,
                new LastCoverage(
                        1,
                        "2024-09",
                        "2026-10",
                        "CHECKED",
                        List.of(new LastCoverage.PeriodCount("2026-03", 10_029)),
                        "2026-09-30T12:00:00Z"));

        HttpResponse<String> response =
                get(sessionCookie(manager), "/api/v1/run-sources?municipalityIbge=" + MUNICIPALITY);

        assertThat(response.body())
                .contains("\"coverageOutcome\":\"CHECKED\"")
                .contains("{\"referencePeriod\":\"2026-03\",\"count\":10029,\"published\":false,"
                        + "\"publishedPacks\":[]}");
    }

    /**
     * ADR 0030: each competência says which packs are published in it and is "published" once every
     * pack the source can compute is; each source lists every runnable pack with its availability —
     * only C1 until the canonical v2 capabilities are validated live.
     */
    @Test
    void eachCompetenciaListsItsPublishedPacksAndEachSourceItsAvailablePacks() throws Exception {
        String municipality = "3509502";
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, municipality);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, municipality);
        sourceRepository.recordCoverage(
                sourceId,
                new LastCoverage(
                        1,
                        "2024-09",
                        "2026-10",
                        "CHECKED",
                        List.of(
                                new LastCoverage.PeriodCount("2026-03", 10),
                                new LastCoverage.PeriodCount("2026-02", 20)),
                        "2026-09-30T12:00:00Z"));
        publishResult(manager, municipality, "2026-03", c1(municipality, "2026-03"), List.of());
        publishResult(
                manager,
                municipality,
                "2026-02",
                "c2-desenvolvimento-infantil",
                RuleOutcomes.pending(
                                new C2Pack().descriptor(),
                                EvaluationContext.endOfMonth(municipality, YearMonth.of(2026, 2)),
                                "Regra em implementação (ADR 0030).")
                        .result(),
                List.of(),
                List.of());

        String body = get(sessionCookie(manager), "/api/v1/run-sources?municipalityIbge=" + municipality)
                .body();

        assertThat(body)
                .contains("{\"referencePeriod\":\"2026-03\",\"count\":10,\"published\":true,"
                        + "\"publishedPacks\":[\"c1-mais-acesso\"]}")
                .contains("{\"referencePeriod\":\"2026-02\",\"count\":20,\"published\":false,"
                        + "\"publishedPacks\":[\"c2-desenvolvimento-infantil\"]}")
                .contains("{\"indicatorPack\":\"c1-mais-acesso\",\"ruleVersion\":\"c1-mais-acesso@0.3.0\","
                        + "\"availability\":\"AVAILABLE\",\"missingCapabilities\":[]}")
                .contains("{\"indicatorPack\":\"c2-desenvolvimento-infantil\","
                        + "\"ruleVersion\":\"c2-desenvolvimento-infantil@0.1.0\",\"availability\":\"UNSUPPORTED_SOURCE\","
                        + "\"missingCapabilities\":[\"citizen\",\"individual_registration\",\"care_encounter\"")
                .contains("\"indicatorPack\":\"c7-prevencao-cancer\"")
                .doesNotContain("componente-iii-nota-final");
    }

    private static IndicatorResult c1(String municipality, String period) {
        return new IndicatorResult(
                IndicatorResult.IndicatorStatus.BLOCKED,
                null,
                BigInteger.ONE,
                BigInteger.TWO,
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                null,
                period,
                "c1-mais-acesso@0.3.0",
                YearMonth.parse(period).atEndOfMonth().toString(),
                municipality,
                List.of(),
                "c1-exact-ratio@1");
    }

    @Test
    void aSwitchWithoutEnabledIsRefusedAndANonPecSourceIsTheOpaque404() throws Exception {
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, MUNICIPALITY);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, MUNICIPALITY);
        String datasetId = "dataset-" + System.nanoTime();
        sourceRepository.upsert(new SourceRecord(
                datasetId,
                1,
                "EXTERNAL_DATASET",
                null,
                null,
                "127.0.0.1", // NOPMD - AvoidUsingHardCodedIP: loopback test server
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                MUNICIPALITY,
                null,
                null,
                Instant.EPOCH.toString()));
        String cookie = reauthenticatedSessionCookie(admin);

        assertThat(authenticatedPut(cookie, URI.create(BASE_URL + "/api/v1/sources/" + sourceId + "/schedule"), "{}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(putSchedule(cookie, datasetId, false).statusCode()).isEqualTo(404);
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
