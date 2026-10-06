package esusdata.indicator.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** Exact score arithmetic for C2–C7 (§2.4); never multiplies a 0–100 score by 100 again. */
public final class Scores {

    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

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
     * C7 (C7-D4): {@code Σ(weight × value) × 100 / Σ weight}, only over the parts that have a
     * denominator. An empty subgroup leaves both the sum and the divisor, so the score is rescaled
     * to the weights present. Empty when no part has a denominator ({@code NO_DENOMINATOR}).
     */
    public static Optional<ExactRatio> weightedMeanOfDefined(List<ResultComponent> parts) {
        ExactRatio total = ExactRatio.zero();
        BigInteger weights = BigInteger.ZERO;
        for (ResultComponent part : parts) {
            if (part.value() != null) {
                total = total.plus(part.value().times(part.weight()));
                weights = weights.add(part.weight());
            }
        }
        if (weights.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ExactRatio(
                total.numerator().multiply(ONE_HUNDRED), total.denominator().multiply(weights)));
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
