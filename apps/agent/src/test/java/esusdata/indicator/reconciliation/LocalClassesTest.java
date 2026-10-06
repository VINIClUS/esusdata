package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c1.C1Pack;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** C1 stands for the packs here: its percentage goes through the same consolidation as the others. */
class LocalClassesTest {

    private static final IndicatorRule C1 = new C1Pack();
    private static final Quadrimestre Q2 = new Quadrimestre(2026, 2);

    private static TeamResult computed(String ine, long numerator, long denominator) {
        return new TeamResult(
                ine,
                null,
                new IndicatorResult(
                        IndicatorStatus.COMPUTED,
                        "x",
                        BigInteger.valueOf(numerator),
                        BigInteger.valueOf(denominator),
                        "invented",
                        null,
                        "2026-05",
                        C1.descriptor().ruleVersion(),
                        "2026-05-31",
                        "9999999",
                        List.of(),
                        "policy"));
    }

    private static TeamResult noDenominator(String ine) {
        return new TeamResult(
                ine,
                null,
                new IndicatorResult(
                        IndicatorStatus.NO_DENOMINATOR,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        "invented",
                        null,
                        "2026-05",
                        C1.descriptor().ruleVersion(),
                        "2026-05-31",
                        "9999999",
                        List.of(),
                        "policy"));
    }

    /** The same teams in the four months. */
    private static Map<YearMonth, List<TeamResult>> everyMonth(TeamResult... teams) {
        Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
        for (YearMonth month : Q2.months()) {
            months.put(month, List.of(teams));
        }
        return months;
    }

    @Test
    void classifiesEachTeamByTheMeanOfTheFourMonthsWithTheFichasBands() {
        Map<YearMonth, List<TeamResult>> months = everyMonth(
                computed("11", 60, 100), // 60 %: Ótimo (50 < x <= 70)
                computed("12", 40, 100), // 40 %: Bom
                computed("13", 80, 100), // 80 %: Regular, the non-monotonic band of C1
                computed("14", 5, 100)); // 5 %: Regular

        LocalClasses classes = LocalClasses.of(C1, Q2, months);

        assertThat(classes.byIne())
                .containsEntry("0000000011", Classification.OTIMO)
                .containsEntry("0000000012", Classification.BOM)
                .containsEntry("0000000013", Classification.REGULAR)
                .containsEntry("0000000014", Classification.REGULAR);
        assertThat(classes.seen()).hasSize(4);
    }

    @Test
    void theMeanIsExactAndBeforeTheBand() {
        Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
        List<YearMonth> list = Q2.months();
        months.put(list.get(0), List.of(computed("11", 80, 100)));
        months.put(list.get(1), List.of(computed("11", 80, 100)));
        months.put(list.get(2), List.of(computed("11", 10, 100)));
        months.put(list.get(3), List.of(computed("11", 10, 100)));

        // each month alone is Regular or Regular; their mean, 45 %, is Bom
        assertThat(LocalClasses.of(C1, Q2, months).byIne()).containsEntry("0000000011", Classification.BOM);
    }

    @Test
    void aTeamMissingFromOneMonthHasNoLocalClassButIsSeen() {
        Map<YearMonth, List<TeamResult>> months = everyMonth(computed("11", 60, 100), computed("12", 60, 100));
        months.put(Q2.months().get(2), List.of(computed("11", 60, 100)));

        LocalClasses classes = LocalClasses.of(C1, Q2, months);

        assertThat(classes.byIne()).containsOnlyKeys("0000000011");
        assertThat(classes.seen()).containsExactlyInAnyOrder("0000000011", "0000000012");
    }

    @Test
    void aMonthWithoutDenominatorLeavesTheTeamWithoutAClass() {
        Map<YearMonth, List<TeamResult>> months = everyMonth(computed("11", 60, 100));
        months.put(Q2.months().get(0), List.of(noDenominator("11")));

        assertThat(LocalClasses.of(C1, Q2, months).byIne()).isEmpty();
    }

    @Test
    void anUnpaddedIneMeetsThePaddedOneOfTheSiapsList() {
        LocalClasses classes = LocalClasses.of(C1, Q2, everyMonth(computed("0000000011", 60, 100)));
        LocalClasses unpadded = LocalClasses.of(C1, Q2, everyMonth(computed("11", 60, 100)));

        assertThat(classes.byIne()).isEqualTo(unpadded.byIne());
        assertThat(unpadded.byIne()).containsKey("0000000011");
    }

    @Test
    void teamsWithoutAnIneAreLeftOut() {
        LocalClasses classes = LocalClasses.of(C1, Q2, everyMonth(computed(null, 60, 100)));

        assertThat(classes.seen()).isEmpty();
    }

    @Test
    void aMonthMissingAltogetherIsRefused() {
        Map<YearMonth, List<TeamResult>> months = everyMonth(computed("11", 60, 100));
        YearMonth gone = Q2.months().get(1);
        months.remove(gone);

        assertThat(LocalClasses.missingMonths(Q2, months)).containsExactly(gone);
        assertThatThrownBy(() -> LocalClasses.of(C1, Q2, months))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(gone.toString());
        assertThat(new ArrayList<>(LocalClasses.missingMonths(Q2, Map.of()))).hasSize(4);
    }
}
