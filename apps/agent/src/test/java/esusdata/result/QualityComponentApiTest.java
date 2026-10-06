package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * {@code GET /api/v1/quality-component} (ADR 0030, NT 8/2026): the Nota Final read from the newest
 * published result of each pack of C1–C7 and month of the quadrimestre — one unit for the
 * municipality and one per team of those results — with the fingerprint of the ids read. While the
 * consolidation is not released, every unit is {@code BLOCKED}, never a score. Same scope as {@code
 * GET /results}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QualityComponentApiTest extends ApiFixtureSupport {

    @Autowired
    ResultRepository resultRepository;

    @Test
    void theNotaFinalReadsThePublishedResultsOfTheQuadrimestreByUnit() throws Exception {
        String ibge = "3509809";
        String manager = createUser("manager-q-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, ibge);
        publishResult(manager, ibge, "2026-01", c1(ibge, "2026-01"), List.of());
        publishResult(manager, ibge, "2026-05", c1(ibge, "2026-05"), List.of()); // the next quadrimestre
        IndicatorResult c2 = RuleOutcomes.pending(
                        new C2Pack().descriptor(),
                        GateStatus.pending(new C2Pack().descriptor()),
                        EvaluationContext.endOfMonth(ibge, YearMonth.of(2026, 2)),
                        "Regra em implementação (ADR 0030).")
                .result();
        publishResult(
                manager,
                ibge,
                "2026-02",
                C2Pack.ID,
                c2,
                List.of(
                        new TeamResult("0000000002", "2750333", c2),
                        new TeamResult("0000000001", "2750325", c2),
                        new TeamResult(null, null, c2)),
                List.of());
        publishResult(manager, ibge, "2026-03", "teste-praticas", c1(ibge, "2026-03"), List.of(), List.of());

        HttpResponse<String> response = get(manager, ibge, "2026-Q1");

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> read = resultRepository.findLatestPublishedInRange(ibge, null, "2026-01", "2026-04").stream()
                .filter(result -> Set.of("c1-mais-acesso", C2Pack.ID).contains(result.indicatorPack()))
                .map(PublishedResult::resultId)
                .sorted()
                .toList();
        assertThat(read).hasSize(2);
        assertThat(response.body())
                .contains("\"municipalityIbge\":\"" + ibge + "\",\"quadrimestre\":\"2026-Q1\","
                        + "\"months\":[\"2026-01\",\"2026-02\",\"2026-03\",\"2026-04\"],"
                        + "\"ruleVersion\":\"" + ComponentIII.RULE_VERSION + "\","
                        + "\"inputFingerprint\":\"" + sha256(String.join("\n", read)) + "\"")
                .contains("\"units\":[{\"ine\":null,\"cnes\":null,\"status\":\"BLOCKED\",\"score\":null,"
                        + "\"scoreExact\":null")
                .contains("{\"ine\":\"0000000001\",\"cnes\":\"2750325\",\"status\":\"BLOCKED\"")
                .contains("{\"ine\":\"0000000002\",\"cnes\":\"2750333\",\"status\":\"BLOCKED\"")
                .contains("\"limitations\":[\""); // the consolidation says why, whatever its version
        assertThat(response.body().indexOf("\"ine\":\"0000000001\""))
                .isLessThan(response.body().indexOf("\"ine\":\"0000000002\""));
        // C2's bucket of records without a team never becomes a second municipal unit.
        assertThat(response.body().indexOf("{\"ine\":null"))
                .isEqualTo(response.body().lastIndexOf("{\"ine\":null"));
    }

    @Test
    void aMalformedQuadrimestreIsABadRequestAndATeamGrantNeverReachesTheMunicipalNote() throws Exception {
        String ibge = "3509908";
        String manager = createUser("manager-q2-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, ibge);
        String team = createUser("team-q2-" + System.nanoTime());
        grantTeam(team, Role.TEAM_SCOPED_PROFESSIONAL, ibge, "2750325", "0000000001");

        assertThat(get(manager, ibge, "2026-Q4").statusCode()).isEqualTo(400);
        assertThat(get(manager, ibge, "2026-T1").statusCode()).isEqualTo(400);
        assertThat(get(team, ibge, "2026-Q1").statusCode()).isEqualTo(404);
        HttpResponse<String> empty = get(manager, ibge, "2026-Q2");
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body())
                .contains("\"inputFingerprint\":\"" + sha256("") + "\"")
                .contains("\"units\":[{\"ine\":null,\"cnes\":null,\"status\":\"BLOCKED\"");
    }

    private static IndicatorResult c1(String ibge, String period) {
        return new IndicatorResult(
                IndicatorStatus.BLOCKED,
                null,
                BigInteger.ONE,
                BigInteger.TWO,
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                null,
                period,
                "c1-mais-acesso@0.2.0",
                YearMonth.parse(period).atEndOfMonth().toString(),
                ibge,
                List.of(),
                "c1-exact-ratio@1");
    }

    private static String sha256(String text) throws Exception {
        return "sha256:"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> get(String userId, String ibge, String quadrimestre) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/quality-component?municipalityIbge=" + ibge
                                    + "&quadrimestre=" + quadrimestre))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
