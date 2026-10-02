package esusdata.indicator.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** Exact score arithmetic for C2–C7 (§2.4); never multiplies a 0–100 score by 100 again. */
public final class Scores {

    private Scores() {}

    /**
     * C2–C6: {@code soma dos pontos / número de pessoas ou episódios elegíveis}. Empty when nobody
     * is eligible — {@code NO_DENOMINATOR}, never zero (MET-04).
     */
    public static Optional<ExactRatio> meanPoints(BigInteger totalPoints, BigInteger subjects) {
        if (subjects.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ExactRatio(totalPoints, subjects));
    }

    /**
     * C7: {@code Σ weight × numerator/denominator}. Empty when any part has no denominator: the
     * ficha does not say how to score an empty subpopulation (P10), so the score stays undefined
     * rather than renormalized or zeroed.
     */
    public static Optional<ExactRatio> weightedSum(List<ResultComponent> parts) {
        ExactRatio total = ExactRatio.zero();
        for (ResultComponent part : parts) {
            if (part.value() == null) {
                return Optional.empty();
            }
            total = total.plus(part.value().times(part.weight()));
        }
        return Optional.of(total);
    }

    /** Points a subject earns: the sum of the weights of the components it satisfied. */
    public static BigInteger points(List<ComponentSpec> satisfied) {
        BigInteger sum = BigInteger.ZERO;
        for (ComponentSpec spec : satisfied) {
            sum = sum.add(spec.weight());
        }
        return sum;
    }
}
