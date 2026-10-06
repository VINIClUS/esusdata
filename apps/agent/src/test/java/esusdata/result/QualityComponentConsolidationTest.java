package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Nota Final from end to end (ADR 0030, NT 8/2026): published monthly results of C1–C7, for
 * the municipality and per team, read by {@code GET /api/v1/quality-component} and consolidated by
 * {@code Nt08Consolidation} — the score, the methodological and the financial classifications
 * (Portaria GM/MS nº 10.994/2026; none for the municipality), the limitations and the fingerprint
 * of the ids read. A month still open by the service's clock is never read.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QualityComponentConsolidationTest extends ApiFixtureSupport {

    private static final String IBGE = "3510005";
    private static final String TEAM_A = "0000000011";
    private static final String TEAM_B = "0000000012";
    private static final List<String> Q2_2026 = List.of("2026-05", "2026-06", "2026-07", "2026-08");

    /** Per pack, the four monthly values of Q2/2026; {@code null} = month without a cohort event. */
    private static final Map<String, List<Long>> VALUES = Map.of(
            "c1-mais-acesso", List.of(40L, 40L, 40L, 40L), // 40 -> Bom (C1 bands)
            "c2-desenvolvimento-infantil", Arrays.asList(80L, null, 85L, null), // 82,5 -> Ótimo (MET-34)
            "c3-gestacao-puerperio", List.of(90L, 90L, 90L, 90L),
            "c4-cuidado-diabetes", List.of(60L, 60L, 60L, 60L), // Bom
            "c5-cuidado-hipertensao", List.of(90L, 90L, 90L, 90L),
            "c6-cuidado-pessoa-idosa", List.of(90L, 90L, 90L, 90L),
            "c7-prevencao-cancer", List.of(90L, 90L, 90L, 90L));

    @Test
    void publishedMonthlyResultsOfC1ToC7YieldTheNotaFinalPerUnit() throws Exception {
        String manager = createUser("manager-c3-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, IBGE);
        List<String> read = new ArrayList<>();
        for (Map.Entry<String, List<Long>> pack : VALUES.entrySet()) {
            for (int i = 0; i < Q2_2026.size(); i++) {
                String month = Q2_2026.get(i);
                Long value = pack.getValue().get(i);
                IndicatorResult result = result(pack.getKey(), month, value);
                // team B: hypertension blocked in June
                IndicatorResult teamB = "c5-cuidado-hipertensao".equals(pack.getKey()) && i == 1
                        ? blocked(pack.getKey(), month)
                        : result;
                read.add(publishResult(
                        manager,
                        IBGE,
                        month,
                        pack.getKey(),
                        result,
                        List.of(new TeamResult(TEAM_A, "2750401", result), new TeamResult(TEAM_B, "2750402", teamB)),
                        List.of()));
            }
        }
        // a competência still open by the service's clock is never read
        String open = YearMonth.now(clock).toString();
        String openId = publishResult(
                manager, IBGE, open, "c1-mais-acesso", result("c1-mais-acesso", open, 40L), List.of(), List.of());

        HttpResponse<String> response = get(manager, "2026-Q2");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = new ObjectMapper().readTree(response.body());
        assertThat(body.get("ruleVersion").asString()).isEqualTo(ComponentIII.RULE_VERSION);
        assertThat(body.get("inputFingerprint").asString())
                .isEqualTo(sha256(String.join("\n", read.stream().sorted().toList())));
        assertThat(read).doesNotContain(openId);
        assertThat(body.get("limitations").toString()).contains("Dependência dos portões de C1–C7");

        JsonNode units = body.get("units");
        assertThat(units).hasSize(3);
        // 1·0,75 + 2·1 + 2·1 + 1·0,75 + 1·1 + 1·1 + 2·1 = 9,5
        JsonNode municipality = units.get(0);
        assertComputed(municipality, null, "9.5000", "19", "2", Classification.OTIMO);
        assertThat(municipality.get("financialTransferClassification").isNull()).isTrue();
        assertThat(municipality.get("limitations").toString()).contains("repasse é por equipe");

        JsonNode teamA = units.get(1);
        assertComputed(teamA, TEAM_A, "9.5000", "19", "2", Classification.OTIMO);
        // Q2/2026, § 3º I: Ótimo keeps Ótimo
        assertThat(teamA.get("financialTransferClassification").asString()).isEqualTo("OTIMO");
        JsonNode c2 = indicator(teamA, "c2-desenvolvimento-infantil");
        assertThat(c2.get("monthsUsed").toString()).isEqualTo("[\"2026-05\",\"2026-07\"]");
        assertThat(c2.get("resultIds")).hasSize(4); // every result read, the months without cohort included
        assertThat(c2.get("meanExact").get("numerator").asString()).isEqualTo("165");
        assertThat(c2.get("meanExact").get("denominator").asString()).isEqualTo("2");
        assertThat(c2.get("classification").asString()).isEqualTo("OTIMO");
        assertThat(c2.get("factor").asString()).isEqualTo("1.00");
        JsonNode c1 = indicator(teamA, "c1-mais-acesso");
        assertThat(c1.get("mean").asString()).isEqualTo("40.0000");
        assertThat(c1.get("classification").asString()).isEqualTo("BOM");
        assertThat(c1.get("factor").asString()).isEqualTo("0.75");

        JsonNode teamB = units.get(2);
        assertThat(teamB.get("ine").asString()).isEqualTo(TEAM_B);
        assertThat(teamB.get("status").asString()).isEqualTo("BLOCKED");
        assertThat(teamB.get("score").isNull()).isTrue();
        assertThat(teamB.get("methodologicalClassification").isNull()).isTrue();
        assertThat(teamB.get("financialTransferClassification").isNull()).isTrue();
        assertThat(teamB.get("limitations").toString()).contains("2026-06").contains("BLOCKED");
        assertThat(indicator(teamB, "c5-cuidado-hipertensao").get("status").asString())
                .isEqualTo("BLOCKED");
    }

    private static void assertComputed(
            JsonNode unit, String ine, String score, String numerator, String denominator, Classification band) {
        assertThat(unit.get("ine").isNull() ? null : unit.get("ine").asString()).isEqualTo(ine);
        assertThat(unit.get("status").asString()).as(unit.toString()).isEqualTo("COMPUTED");
        assertThat(unit.get("score").asString()).isEqualTo(score);
        assertThat(unit.get("scoreExact").get("numerator").asString()).isEqualTo(numerator);
        assertThat(unit.get("scoreExact").get("denominator").asString()).isEqualTo(denominator);
        assertThat(unit.get("methodologicalClassification").asString()).isEqualTo(band.name());
        assertThat(unit.get("indicators")).hasSize(7);
    }

    private static JsonNode indicator(JsonNode unit, String pack) {
        for (JsonNode indicator : unit.get("indicators")) {
            if (pack.equals(indicator.get("indicatorPack").asString())) {
                return indicator;
            }
        }
        throw new AssertionError("no indicator " + pack);
    }

    /** A computed month, or — {@code value == null} — a month without a cohort event (C2/C3). */
    private static IndicatorResult result(String pack, String period, Long value) {
        if (value == null) {
            return month(pack, period, IndicatorStatus.NO_DENOMINATOR, null, null, false);
        }
        BigInteger[] pair = "c1-mais-acesso".equals(pack)
                ? new BigInteger[] {BigInteger.valueOf(value), BigInteger.valueOf(100)}
                : null;
        return month(pack, period, IndicatorStatus.COMPUTED, ExactRatio.of(value, 1), pair, true);
    }

    private static IndicatorResult blocked(String pack, String period) {
        return month(pack, period, IndicatorStatus.BLOCKED, null, null, true);
    }

    /** C1 is a percentage with numerator and denominator ({@code pair}); C2–C7 are scores. */
    private static IndicatorResult month(
            String pack, String period, IndicatorStatus status, ExactRatio value, BigInteger[] pair, boolean eligible) {
        boolean c1 = "c1-mais-acesso".equals(pack);
        return new IndicatorResult(
                status,
                value == null ? null : value.toScaledBigDecimal(4).toPlainString(),
                pair == null ? null : pair[0],
                pair == null ? null : pair[1],
                c1 ? "PROGRAMADOS_MAIS_ESPONTANEOS" : null,
                null,
                period,
                c1 ? C1Rule.RULE_VERSION : pack + (pack.matches("c[456]-.*") ? "@0.2.0" : "@0.1.0"),
                YearMonth.parse(period).atEndOfMonth().toString(),
                IBGE,
                List.of(),
                pack + "-policy@1",
                c1 ? ValueKind.PERCENTAGE : ValueKind.SCORE,
                value,
                List.of(),
                eligible);
    }

    private static String sha256(String text) throws Exception {
        return "sha256:"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> get(String userId, String quadrimestre) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/quality-component?municipalityIbge=" + IBGE
                                    + "&quadrimestre=" + quadrimestre))
                            .header("Cookie", sessionCookie(userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
