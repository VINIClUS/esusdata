package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** §2.4: mean of points for C2–C6, weighted subpopulations for C7 — no ×100, no renormalization. */
class ScoresTest {

    private static final ComponentSpec A = ComponentSpec.subgroup("A", "A", 20, "36 meses");
    private static final ComponentSpec B = ComponentSpec.subgroup("B", "B", 30, "9 a 14 anos");
    private static final ComponentSpec C = ComponentSpec.subgroup("C", "C", 30, "12 meses");
    private static final ComponentSpec D = ComponentSpec.subgroup("D", "D", 20, "24 meses");

    private static ResultComponent part(ComponentSpec spec, long n, long d) {
        return ResultComponent.of(spec, BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    @Test
    void met24_weightedSubpopulationsScoreForty() {
        // A=1/2, B=1/4, C=3/4, D=0/2 -> 10 + 7.5 + 22.5 + 0 = 40, each with its own denominator
        ExactRatio score = Scores.weightedSum(List.of(part(A, 1, 2), part(B, 1, 4), part(C, 3, 4), part(D, 0, 2)))
                .orElseThrow();
        assertThat(score.compareTo(ExactRatio.of(40, 1))).isZero();
    }

    @Test
    void p10_anEmptySubpopulationLeavesTheScoreUndefined() {
        assertThat(Scores.weightedSum(List.of(part(A, 1, 2), part(B, 0, 0), part(C, 3, 4), part(D, 1, 2))))
                .isEmpty();
    }

    @Test
    void met20_meanPointsIsAlreadyOnTheZeroToHundredScale() {
        // one person with all eleven C3 practices: 10 + 10 × 9 = 100 points, not 10 000
        assertThat(Scores.meanPoints(BigInteger.valueOf(100), BigInteger.ONE))
                .hasValueSatisfying(
                        v -> assertThat(v.compareTo(ExactRatio.of(100, 1))).isZero());
        assertThat(Scores.points(List.of(
                        ComponentSpec.practice("A", "A", 10, "12ª semana"),
                        ComponentSpec.practice("B", "B", 9, "gestação"))))
                .isEqualTo(BigInteger.valueOf(19));
    }

    @Test
    void met04_noEligibleSubjectHasNoMean() {
        assertThat(Scores.meanPoints(BigInteger.ZERO, BigInteger.ZERO)).isEmpty();
    }
}
