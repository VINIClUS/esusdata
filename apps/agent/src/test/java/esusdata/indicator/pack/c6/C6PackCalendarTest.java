package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.exclusionReason;
import static esusdata.indicator.pack.c6.C6Scenario.met;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.RuleOutcome;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/**
 * ENG-27 calendar edges for C6: the 12 civil months ending on the last day of the competência
 * (AMB-C6-02), age by civil anniversary on that day (AMB-C6-05) and the 30-day visit interval
 * (AMB-C6-03) across February.
 */
class C6PackCalendarTest {

    private static final String KEY = "pessoa";

    @Test
    void eng27_firstDayOfTheWindowCounts() {
        assertThat(consultMeetsA(LocalDate.of(2025, 4, 1), YearMonth.of(2026, 3)))
                .isTrue();
    }

    @Test
    void eng27_lastDayOfTheCompetenciaCounts() {
        assertThat(consultMeetsA(LocalDate.of(2026, 3, 31), YearMonth.of(2026, 3)))
                .isTrue();
    }

    @Test
    void eng27_dayAfterTheCompetenciaDoesNotCount() {
        assertThat(consultMeetsA(LocalDate.of(2026, 4, 1), YearMonth.of(2026, 3)))
                .isFalse();
    }

    @Test
    void eng27_dayBeforeTheWindowDoesNotCount() {
        assertThat(consultMeetsA(LocalDate.of(2025, 3, 31), YearMonth.of(2026, 3)))
                .isFalse();
    }

    @Test
    void eng27_februaryCompetenciaWindowStartsOnMarchFirst() {
        YearMonth february = YearMonth.of(2026, 2);
        assertThat(consultMeetsA(LocalDate.of(2025, 3, 1), february)).isTrue();
        assertThat(consultMeetsA(LocalDate.of(2025, 2, 28), february)).isFalse();
        assertThat(consultMeetsA(LocalDate.of(2026, 2, 28), february)).isTrue();
        assertThat(consultMeetsA(LocalDate.of(2026, 3, 1), february)).isFalse();
    }

    @Test
    void eng27_bornOnFebruary29EntersInFebruaryOfTheLeapYear() {
        RuleOutcome outcome = leapling().compute(YearMonth.of(2024, 2));

        assertThat(subjectRow(outcome, KEY).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void eng27_bornOnFebruary29IsStill59InJanuary() {
        RuleOutcome outcome = leapling().compute(YearMonth.of(2024, 1));

        assertThat(exclusionReason(outcome, KEY)).isEqualTo("EXCLUIDO_IDADE_MENOR_60");
    }

    @Test
    void c6D3_bornOnFebruary29TurnsSixtyOnMarchFirstWhenTheSixtiethYearIsNotLeap() {
        C6Scenario bornOnLeapDay =
                scenario().person(KEY, LocalDate.of(2040, 2, 29)).linked(KEY, INE_A);

        // 2100 is not a leap year: the 60th anniversary falls on 01/03 (Lei 810/1949, art. 3º), not 28/02.
        assertThat(exclusionReason(bornOnLeapDay.compute(YearMonth.of(2100, 2)), KEY))
                .isEqualTo("EXCLUIDO_IDADE_MENOR_60");
        assertThat(subjectRow(bornOnLeapDay.compute(YearMonth.of(2100, 3)), KEY).decision())
                .isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    @Test
    void eng27_visitsFromJanuary31ToMarch1AreThirtyDaysInALeapYear() {
        RuleOutcome outcome = scenario()
                .elder(KEY)
                .visit(KEY, LocalDate.of(2024, 1, 31))
                .visit(KEY, LocalDate.of(2024, 3, 1))
                .compute(YearMonth.of(2024, 3));

        assertThat(met(outcome, KEY, "C")).isTrue();
    }

    @Test
    void eng27_visitsFromJanuary31ToMarch1AreTwentyNineDaysInACommonYear() {
        RuleOutcome outcome = scenario()
                .elder(KEY)
                .visit(KEY, LocalDate.of(2025, 1, 31))
                .visit(KEY, LocalDate.of(2025, 3, 1))
                .compute(YearMonth.of(2025, 3));

        assertThat(met(outcome, KEY, "C")).isFalse();
    }

    @Test
    void eng27_aVisitAfterTheCutoffDoesNotCompleteTheInterval() {
        RuleOutcome outcome = scenario()
                .elder(KEY)
                .visit(KEY, LocalDate.of(2026, 3, 10))
                .visit(KEY, LocalDate.of(2026, 4, 15))
                .compute();

        assertThat(met(outcome, KEY, "C")).isFalse();
    }

    private static boolean consultMeetsA(LocalDate consult, YearMonth competencia) {
        RuleOutcome outcome = scenario().elder(KEY).consult(KEY, consult).compute(competencia);
        return met(outcome, KEY, "A");
    }

    private static C6Scenario leapling() {
        return scenario().person(KEY, LocalDate.of(1964, 2, 29)).linked(KEY, INE_A);
    }
}
