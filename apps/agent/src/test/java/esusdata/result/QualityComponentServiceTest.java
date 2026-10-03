package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIIIInput;
import esusdata.indicator.pack.componente3.ComponentIIIResult;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How published results become the units of the Nota Final (ADR 0030, NT 8/2026). */
class QualityComponentServiceTest {

    private static final String IBGE = "3541307";

    /** Every month of 2026 closed. */
    private static final Clock NEXT_YEAR = Clock.fixed(Instant.parse("2027-01-15T12:00:00Z"), ZoneOffset.UTC);

    private static PublishedResult result(
            String id, String pack, String period, String status, String exactNumerator, String teamsJson) {
        return new PublishedResult(
                id,
                "job",
                "run",
                "src",
                pack,
                pack + "@0.1.0",
                IBGE,
                period,
                status,
                null,
                null,
                null,
                null,
                null,
                period + "-28",
                "ext",
                "0.1.0",
                "p@1",
                "[]",
                "fp",
                "LOCAL_ESTIMATE",
                "NOT_VALIDATED",
                "COMPLETE",
                "SNAPSHOT",
                "REPRODUCIBLE",
                "2",
                "SUBJECT_PRACTICE",
                "dev",
                "2026-05-01T00:00:00Z",
                "SCORE",
                exactNumerator,
                exactNumerator == null ? null : "1",
                "[]",
                teamsJson,
                false);
    }

    private static String team(String ine, String cnes, String status, String valueNumerator) {
        return "{\"ine\":" + (ine == null ? "null" : "\"" + ine + "\"") + ",\"cnes\":"
                + (cnes == null ? "null" : "\"" + cnes + "\"") + ",\"status\":\"" + status + "\",\"valueText\":null,"
                + "\"valueNumerator\":" + (valueNumerator == null ? "null" : "\"" + valueNumerator + "\"")
                + ",\"valueDenominator\":" + (valueNumerator == null ? "null" : "\"1\"")
                + ",\"numerator\":null,\"denominator\":null,\"denominatorKind\":null,\"classification\":null,"
                + "\"consolidationEligible\":true,\"components\":[],\"limitations\":[]}";
    }

    @Test
    void theMunicipalityComesFirstThenOneUnitPerTeamWithValuesOnlyWhenComputed() {
        List<ComponentIIIInput.Unit> units = QualityComponentService.units(List.of(
                result(
                        "res-a",
                        "c4-cuidado-diabetes",
                        "2026-01",
                        "COMPUTED",
                        "60",
                        "[" + team("0000000002", null, "COMPUTED", "70") + ","
                                + team("0000000001", "2750325", "BLOCKED", "10") + ","
                                + team(null, null, "COMPUTED", "5") + "]"),
                result(
                        "res-b",
                        "c4-cuidado-diabetes",
                        "2026-02",
                        "BLOCKED",
                        null,
                        "[" + team("0000000002", "2750333", "COMPUTED", "80") + "]")));

        assertThat(units).extracting(ComponentIIIInput.Unit::ine).containsExactly(null, "0000000001", "0000000002");
        ComponentIIIInput.Unit municipality = units.getFirst();
        assertThat(municipality.monthly())
                .extracting(
                        ComponentIIIInput.Monthly::resultId,
                        ComponentIIIInput.Monthly::month,
                        ComponentIIIInput.Monthly::status,
                        ComponentIIIInput.Monthly::value,
                        ComponentIIIInput.Monthly::consolidationEligible)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "res-a", YearMonth.of(2026, 1), IndicatorStatus.COMPUTED, ExactRatio.of(60, 1), false),
                        org.assertj.core.groups.Tuple.tuple(
                                "res-b", YearMonth.of(2026, 2), IndicatorStatus.BLOCKED, null, false));
        assertThat(units.get(1).cnes()).isEqualTo("2750325");
        assertThat(units.get(1).monthly().getFirst().value()).isNull(); // BLOCKED: never a value
        ComponentIIIInput.Unit second = units.get(2);
        assertThat(second.cnes()).isEqualTo("2750333");
        assertThat(second.monthly())
                .extracting(ComponentIIIInput.Monthly::value)
                .containsExactly(ExactRatio.of(70, 1), ExactRatio.of(80, 1));
    }

    @Test
    void theFingerprintIsTheSha256OfTheSortedIdsRead() {
        String forward = QualityComponentService.fingerprint(List.of(
                result("res-b", "c1-mais-acesso", "2026-01", "BLOCKED", null, "[]"),
                result("res-a", "c1-mais-acesso", "2026-02", "BLOCKED", null, "[]")));
        String backward = QualityComponentService.fingerprint(List.of(
                result("res-a", "c1-mais-acesso", "2026-02", "BLOCKED", null, "[]"),
                result("res-b", "c1-mais-acesso", "2026-01", "BLOCKED", null, "[]")));

        assertThat(forward).isEqualTo(backward).startsWith("sha256:").hasSize(71);
    }

    @Test
    void onlyThePacksOfTheNoteAreReadAndEveryUnitStaysBlockedUntilTheConsolidationIsReleased() {
        ResultRepository results = mock(ResultRepository.class);
        when(results.findLatestPublishedInRange(IBGE, null, "2026-05", "2026-08"))
                .thenReturn(List.of(
                        result("res-c1", "c1-mais-acesso", "2026-05", "BLOCKED", null, "[]"),
                        result("res-x", "teste-praticas", "2026-05", "COMPUTED", "50", "[]")));

        QualityComponentService.Consolidated consolidated =
                new QualityComponentService(results, NEXT_YEAR).consolidate(IBGE, Quadrimestre.parse("2026-Q2"));

        assertThat(consolidated.inputFingerprint())
                .isEqualTo(QualityComponentService.fingerprint(
                        List.of(result("res-c1", "c1-mais-acesso", "2026-05", "BLOCKED", null, "[]"))));
        assertThat(consolidated.result().units()).singleElement().satisfies(unit -> {
            assertThat(unit.ine()).isNull();
            assertThat(unit.status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(unit.score()).isNull();
        });
    }

    @Test
    void theNoTeamBucketNeverBecomesASecondMunicipality() {
        String bucket = team(null, null, "COMPUTED", "5");
        List<ComponentIIIInput.Unit> onlyBucket = QualityComponentService.units(
                List.of(result("res-a", "c5-cuidado-hipertensao", "2026-01", "COMPUTED", "60", "[" + bucket + "]")));
        List<ComponentIIIInput.Unit> teamAndBucket = QualityComponentService.units(List.of(result(
                "res-b",
                "c5-cuidado-hipertensao",
                "2026-02",
                "COMPUTED",
                "60",
                "[" + bucket + "," + team("0000000001", "2750325", "COMPUTED", "70") + "]")));

        assertThat(onlyBucket).singleElement().satisfies(unit -> {
            assertThat(unit.ine()).isNull();
            assertThat(unit.monthly())
                    .singleElement()
                    .extracting(ComponentIIIInput.Monthly::value)
                    .isEqualTo(ExactRatio.of(60, 1)); // the municipal result, never the bucket's 5
        });
        assertThat(teamAndBucket).extracting(ComponentIIIInput.Unit::ine).containsExactly(null, "0000000001");
        assertThat(teamAndBucket)
                .allSatisfy(unit -> assertThat(unit.monthly()).hasSize(1))
                .extracting(unit -> unit.monthly().getFirst().value())
                .containsExactly(ExactRatio.of(60, 1), ExactRatio.of(70, 1));
    }

    @Test
    void aCompetenciaTheServiceClockHasNotClosedIsReadAsAbsent() {
        ResultRepository results = mock(ResultRepository.class);
        when(results.findLatestPublishedInRange(IBGE, null, "2026-05", "2026-08"))
                .thenReturn(List.of(
                        result("res-c1-05", "c1-mais-acesso", "2026-05", "BLOCKED", null, "[]"),
                        result("res-c1-06", "c1-mais-acesso", "2026-06", "BLOCKED", null, "[]"),
                        result("res-c1-07", "c1-mais-acesso", "2026-07", "BLOCKED", null, "[]"),
                        result(
                                "res-c4-07",
                                "c4-cuidado-diabetes",
                                "2026-07",
                                "COMPUTED",
                                "80",
                                "[" + team("0000000001", "2750325", "COMPUTED", "90") + "]")));
        Quadrimestre second = Quadrimestre.parse("2026-Q2");
        // 23:00 on 31 July in São Paulo: already August in UTC, but July is still open.
        QualityComponentService lastHourOfJuly = new QualityComponentService(
                results, Clock.fixed(Instant.parse("2026-08-01T02:00:00Z"), ZoneOffset.UTC));
        // Midnight on 1 August in São Paulo: July is closed.
        QualityComponentService firstHourOfAugust = new QualityComponentService(
                results, Clock.fixed(Instant.parse("2026-08-01T03:00:00Z"), ZoneOffset.UTC));

        List<PublishedResult> beforeClose = lastHourOfJuly.read(IBGE, second);
        QualityComponentService.Consolidated consolidated = lastHourOfJuly.consolidate(IBGE, second);

        assertThat(beforeClose).extracting(PublishedResult::resultId).containsExactly("res-c1-05", "res-c1-06");
        assertThat(QualityComponentService.units(beforeClose))
                .singleElement()
                .satisfies(unit -> assertThat(unit.monthly())
                        .extracting(ComponentIIIInput.Monthly::month)
                        .containsExactly(YearMonth.of(2026, 5), YearMonth.of(2026, 6)));
        assertThat(consolidated.inputFingerprint()).isEqualTo(QualityComponentService.fingerprint(beforeClose));
        assertThat(consolidated.result().units()).singleElement().satisfies(unit -> {
            assertThat(unit.ine()).isNull(); // the team only July's C4 named has no unit yet
            assertThat(unit.score()).isNull();
        });
        assertThat(firstHourOfAugust.read(IBGE, second))
                .extracting(PublishedResult::resultId)
                .containsExactly("res-c1-05", "res-c1-06", "res-c1-07", "res-c4-07");
        assertThat(firstHourOfAugust.consolidate(IBGE, second).result().units())
                .extracting(ComponentIIIResult.UnitResult::ine)
                .containsExactly(null, "0000000001");
    }
}
