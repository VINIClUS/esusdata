package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c4.C4Pack;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * The Nota Final side of {@code siaps-nota-final-por-classe@1}: the final class of each team from
 * the ungated monthly results of the seven packs, by the product's own consolidation. Invented data.
 */
class NotaFinalLocalClassesTest {

    private static final Quadrimestre Q2 = new Quadrimestre(2026, 2);
    private static final String A = "0000000011";
    private static final String B = "0000000012";
    private static final String C = "0000000013";

    /** A value on the pack's own scale that its ficha puts in {@code wanted}. */
    private static ExactRatio valueIn(IndicatorRule rule, Classification wanted) {
        for (int n = 0; n <= 20_000; n++) {
            ExactRatio candidate = ExactRatio.of(n, 100);
            if (rule.classify(candidate).filter(wanted::equals).isPresent()) {
                return candidate;
            }
        }
        throw new AssertionError(
                wanted + " is out of reach for " + rule.descriptor().id());
    }

    private static TeamResult result(IndicatorRule rule, String ine, IndicatorStatus status, Classification wanted) {
        boolean computed = status == IndicatorStatus.COMPUTED;
        return new TeamResult(
                ine,
                null,
                new IndicatorResult(
                        status,
                        "x",
                        BigInteger.ONE,
                        BigInteger.ONE,
                        "invented",
                        null,
                        "2026-05",
                        rule.descriptor().ruleVersion(),
                        "2026-05-31",
                        "9999999",
                        List.of(),
                        "policy",
                        ValueKind.PERCENTAGE,
                        computed ? valueIn(rule, wanted) : null,
                        List.of(),
                        true));
    }

    private static TeamResult computed(IndicatorRule rule, String ine, Classification wanted) {
        return result(rule, ine, IndicatorStatus.COMPUTED, wanted);
    }

    /** Every pack, every month of the quadrimestre: {@code teams} says which teams a pack and month have. */
    private static Map<String, Map<YearMonth, List<TeamResult>>> everyPack(
            BiFunction<IndicatorRule, YearMonth, List<TeamResult>> teams) {
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = new LinkedHashMap<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
            for (YearMonth month : Q2.months()) {
                months.put(month, teams.apply(rule, month));
            }
            byPack.put(rule.descriptor().id(), months);
        }
        return byPack;
    }

    @Test
    void theNotaFinalOfATeamIsTheQuadro6ClassOfTheWeightedFactors() {
        // all indicators in one class: the weights sum to 10, so the note is 10 · factor
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = everyPack((rule, month) -> List.of(
                computed(rule, A, Classification.OTIMO), // 10,0: Ótimo
                computed(rule, B, Classification.BOM), // 7,5: Bom (Quadro 6 closes Bom at 7,5)
                computed(rule, C, Classification.REGULAR))); // 2,5: Regular (Quadro 6 closes Regular at 2,5)

        LocalClasses classes = LocalClasses.ofNotaFinal(Q2, byPack);

        assertThat(classes.byIne())
                .containsEntry(A, Classification.OTIMO)
                .containsEntry(B, Classification.BOM)
                .containsEntry(C, Classification.REGULAR);
        assertThat(classes.seen()).containsExactlyInAnyOrder(A, B, C);
    }

    @Test
    void aTeamWithOneUnavailableIndicatorHasNoNotaFinalButIsSeen() {
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = everyPack((rule, month) -> {
            boolean blocked = rule.descriptor().id().equals(C4Pack.ID)
                    && month.equals(Q2.months().get(1));
            return List.of(
                    computed(rule, A, Classification.OTIMO),
                    blocked ? result(rule, B, IndicatorStatus.BLOCKED, null) : computed(rule, B, Classification.OTIMO));
        });

        LocalClasses classes = LocalClasses.ofNotaFinal(Q2, byPack);

        assertThat(classes.byIne()).containsOnlyKeys(A);
        assertThat(classes.seen()).containsExactlyInAnyOrder(A, B);
    }

    @Test
    void aTeamAbsentFromAMonthIsTheDashMonthOfC2WhenTheMunicipalityPublishedIt() {
        // C2 only counts the months with a cohort event: a team without a row in a month the municipality
        // published (team A has it) is out of the mean, not a missing result, as in the product
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = everyPack((rule, month) -> {
            boolean dash = rule.descriptor().id().equals(C2Pack.ID)
                    && month.equals(Q2.months().get(2));
            return dash
                    ? List.of(computed(rule, A, Classification.OTIMO))
                    : List.of(computed(rule, A, Classification.OTIMO), computed(rule, B, Classification.OTIMO));
        });

        assertThat(LocalClasses.ofNotaFinal(Q2, byPack).byIne())
                .containsEntry(A, Classification.OTIMO)
                .containsEntry(B, Classification.OTIMO);
    }

    @Test
    void aTeamAbsentFromAMonthOfAnotherPackHasNoNotaFinal() {
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = everyPack((rule, month) -> {
            boolean gone = rule.descriptor().id().equals(C4Pack.ID)
                    && month.equals(Q2.months().get(2));
            return gone
                    ? List.of(computed(rule, A, Classification.OTIMO))
                    : List.of(computed(rule, A, Classification.OTIMO), computed(rule, B, Classification.OTIMO));
        });

        assertThat(LocalClasses.ofNotaFinal(Q2, byPack).byIne()).containsOnlyKeys(A);
    }

    @Test
    void aMissingPackOrMonthIsRefusedAndNamed() {
        Map<String, Map<YearMonth, List<TeamResult>>> byPack =
                everyPack((rule, month) -> List.of(computed(rule, A, Classification.OTIMO)));
        byPack.get(C4Pack.ID).remove(Q2.months().get(1));
        byPack.remove(C2Pack.ID);

        List<String> missing = LocalClasses.missingNotaFinalInputs(Q2, byPack);

        assertThat(missing)
                .contains("C4 " + Q2.months().get(1))
                .contains("C2 " + Q2.months().getFirst());
        assertThat(LocalClasses.missingNotaFinalInputs(Q2, Map.of())).hasSize(7 * 4);
        assertThatThrownBy(() -> LocalClasses.ofNotaFinal(Q2, byPack))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("C4 " + Q2.months().get(1));
    }
}
