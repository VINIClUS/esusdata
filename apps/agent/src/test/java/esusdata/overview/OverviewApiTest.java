package esusdata.overview;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.source.model.LastCoverage;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ADR 0029 over HTTP. Each test uses its own municipality, so what one publishes never shows in
 * another's overview.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OverviewApiTest extends ApiFixtureSupport {

    @Test
    void aNewInstallationStillGetsItsChecksAndAlertsWithNothingPublished() throws Exception {
        String ibge = "3500105";
        String manager = manager(ibge);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, ibge);

        HttpResponse<String> response = overview(manager, ibge, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"referencePeriod\":null")
                .contains("\"lastUpdate\":null")
                .contains("\"indicatorPack\":\"c1-mais-acesso\"")
                .contains(
                        "{\"code\":\"SOURCE_CONNECTION\",\"sourceId\":\"" + sourceId + "\",\"status\":\"NOT_CHECKED\"")
                .contains("{\"code\":\"PEC_COVERAGE\",\"sourceId\":\"" + sourceId + "\",\"status\":\"NOT_CHECKED\"")
                .contains("{\"code\":\"RESULTS_PUBLISHED\",\"sourceId\":null,\"status\":\"ATTENTION\"")
                .contains("\"code\":\"CHECK_ATTENTION\",\"severity\":\"WARNING\",\"subject\":\"RESULTS_PUBLISHED\"")
                .contains("\"recentRuns\":[]")
                .doesNotContain("PEC_DB_PASSWORD");
    }

    @Test
    void thePublishedCompetenciaFillsIndicatorsHistoryQualityAndLastUpdate() throws Exception {
        String ibge = "3500204";
        String manager = manager(ibge);
        publishResult(manager, ibge, "2026-06", computed(ibge, "2026-06"), List.of());

        String body = overview(manager, ibge, null).body();

        assertThat(body)
                .contains("\"referencePeriod\":\"2026-06\"")
                .contains("\"status\":\"COMPUTED\",\"value\":\"60.0000\",\"limitations\":[],\"publishedAt\":\"")
                .contains("{\"referencePeriod\":\"2026-06\",\"indicatorPack\":\"c1-mais-acesso\","
                        + "\"status\":\"COMPUTED\",\"value\":\"60.0000\"}")
                .contains("\"quality\":{\"published\":1,\"completeSnapshot\":1}")
                .contains("{\"code\":\"RESULTS_PUBLISHED\",\"sourceId\":null,\"status\":\"OK\"")
                .doesNotContain("\"lastUpdate\":null");
        // Another competência asked explicitly: nothing published there, the history still is.
        assertThat(overview(manager, ibge, "2026-07").body())
                .contains("\"referencePeriod\":\"2026-07\"")
                .contains("\"quality\":{\"published\":0,\"completeSnapshot\":0}")
                .contains("\"referencePeriod\":\"2026-06\",\"indicatorPack\":\"c1-mais-acesso\"");
    }

    @Test
    void pendingCompetenciasAreTheSchedulersOwnListAndOneAlertPerSource() throws Exception {
        String ibge = "3500303";
        String manager = manager(ibge);
        String sourceId = "src-" + System.nanoTime();
        registerSource(sourceId, ibge, "5.5.28"); // C1 needs the team type, validated on 5.5.28
        YearMonth current = YearMonth.now(clock);
        String older = current.minusMonths(3).toString();
        String old = current.minusMonths(2).toString();
        sourceRepository.recordCoverage(
                sourceId,
                new LastCoverage(
                        sourceRepository.findById(sourceId).orElseThrow().sourceConfigurationVersion(),
                        current.minusMonths(24).atDay(1).toString(),
                        current.plusMonths(1).atDay(1).toString(),
                        "CHECKED",
                        List.of(
                                new LastCoverage.PeriodCount(current.toString(), 5),
                                new LastCoverage.PeriodCount(old, 900),
                                new LastCoverage.PeriodCount(older, 800)),
                        clock.instant().toString()));

        String body = overview(manager, ibge, null).body();

        // The current month is not settled, so it is not pending. On a PEC 5.5.28 source C1 reads only
        // atendimentos (and the team type), so its competências are the covered ones; the other packs
        // read more and are due from the oldest covered month on.
        String allPacks = "[\"c1-mais-acesso\",\"c2-desenvolvimento-infantil\",\"c3-gestacao-puerperio\","
                + "\"c4-cuidado-diabetes\",\"c5-cuidado-hipertensao\",\"c6-cuidado-pessoa-idosa\","
                + "\"c7-prevencao-cancer\"]";
        assertThat(body)
                .contains("{\"sourceId\":\"" + sourceId + "\",\"referencePeriod\":\"" + older
                        + "\",\"count\":800,\"indicatorPacks\":" + allPacks + "}")
                .contains("{\"sourceId\":\"" + sourceId + "\",\"referencePeriod\":\"" + old
                        + "\",\"count\":900,\"indicatorPacks\":" + allPacks + "}")
                .doesNotContain("\"referencePeriod\":\"" + current + "\",\"count\":5,")
                .contains("\"code\":\"PENDING_PERIODS\",\"severity\":\"INFO\",\"subject\":null,\"referencePeriod\":\""
                        + "2024-10\",\"sourceId\":\"" + sourceId + "\"")
                .contains("{\"code\":\"PEC_COVERAGE\",\"sourceId\":\"" + sourceId + "\",\"status\":\"OK\"");
    }

    /**
     * ADR 0030: every catalog pack says what it is and whether some PEC source of the municipality
     * can compute it — C1 only, until the canonical v2 capabilities are validated live; the Nota
     * Final is computed on read, never run.
     */
    @Test
    void everyIndicatorSaysWhatItIsAndWhetherASourceOfTheMunicipalityCanComputeIt() throws Exception {
        String ibge = "3500709";
        String manager = manager(ibge);

        assertThat(overview(manager, ibge, null).body())
                .contains("{\"indicatorPack\":\"c1-mais-acesso\",\"ruleVersion\":\"c1-mais-acesso@0.5.0\","
                        + "\"family\":\"QUALIDADE_ESF_EAP\",\"unit\":\"percentual\",\"code\":\"C1\","
                        + "\"title\":\"Mais acesso\",\"valueKind\":\"PERCENTAGE\",\"runnable\":true,"
                        + "\"availability\":\"NO_SOURCE\",\"missingCapabilities\":[]");

        registerSource("src-" + System.nanoTime(), ibge);
        String body = overview(manager, ibge, null).body();

        assertThat(body)
                // C1 reads the team type (ADR 0033), validated for PEC 5.5.28 only; this source is 5.4.37
                .contains("\"code\":\"C1\",\"title\":\"Mais acesso\",\"valueKind\":\"PERCENTAGE\",\"runnable\":true,"
                        + "\"availability\":\"UNSUPPORTED_SOURCE\",\"missingCapabilities\":[\"team\"]")
                .contains("\"code\":\"C2\",\"title\":\"Cuidado no desenvolvimento infantil\",\"valueKind\":\"SCORE\","
                        + "\"runnable\":true,\"availability\":\"UNSUPPORTED_SOURCE\",\"missingCapabilities\":"
                        + "[\"citizen\",\"individual_registration\",\"care_encounter\"")
                .contains("\"code\":\"C7\"")
                .contains("\"valueKind\":\"COMPOSITE_SCORE\"")
                .contains("\"indicatorPack\":\"componente-iii-nota-final\"")
                .contains("\"valueKind\":\"FINAL_SCORE\",\"runnable\":false,\"availability\":\"UNSUPPORTED_SOURCE\"");
    }

    @Test
    void anAuditorReadsTheOverviewWithoutTheMunicipalitysJobs() throws Exception {
        String ibge = "3500402";
        String auditor = createUser("auditor-" + System.nanoTime());
        grantMunicipality(auditor, Role.AUDITOR, ibge);

        HttpResponse<String> response = overview(auditor, ibge, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"recentRuns\":null");
    }

    @Test
    void neitherTheTechnicalAdminNorATeamScopedGrantOpensTheMunicipalAggregate() throws Exception {
        String ibge = "3500501";
        String admin = createUser("admin-" + System.nanoTime());
        grantMunicipality(admin, Role.TECHNICAL_ADMIN, ibge);
        String team = createUser("team-" + System.nanoTime());
        grantTeam(team, Role.TEAM_SCOPED_PROFESSIONAL, ibge, "2000001", "0000000001");

        // Out of scope answers like an unknown municipality: the opaque 404 of GET /results.
        assertThat(overview(admin, ibge, null).statusCode()).isEqualTo(404);
        assertThat(overview(team, ibge, null).statusCode()).isEqualTo(404);
    }

    @Test
    void aMalformedCompetenciaIsABadRequest() throws Exception {
        String ibge = "3500600";
        assertThat(overview(manager(ibge), ibge, "03/2026").statusCode()).isEqualTo(400);
    }

    private String manager(String ibge) {
        String manager = createUser("manager-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, ibge);
        return manager;
    }

    private static IndicatorResult computed(String ibge, String period) {
        return new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED,
                "60.0000",
                BigInteger.valueOf(3),
                BigInteger.valueOf(5),
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                Classification.BOM,
                period,
                "c1-mais-acesso@0.5.0",
                YearMonth.parse(period).atEndOfMonth().toString(),
                ibge,
                List.of(),
                "c1-exact-ratio@1");
    }

    private HttpResponse<String> overview(String userId, String ibge, String period) throws Exception {
        String query = "municipalityIbge=" + ibge + (period == null ? "" : "&referencePeriod=" + period);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/overview?" + query))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
