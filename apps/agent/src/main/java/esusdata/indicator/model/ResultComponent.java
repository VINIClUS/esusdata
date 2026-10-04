package esusdata.indicator.model;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import java.math.BigInteger;
import java.util.Objects;

/**
 * The exact counts behind one practice or subgroup of a result: how many eligible people or
 * episodes ({@code denominator}) and how many of them satisfied it ({@code numerator}). {@code
 * value} is {@code numerator/denominator} as a fraction (0–1, never ×100), or {@code null} with
 * {@code NO_DENOMINATOR} — an empty subgroup stays undefined, never zero (Tech Spec P10).
 */
public record ResultComponent(
        String code,
        ComponentKind kind,
        BigInteger weight,
        BigInteger numerator,
        BigInteger denominator,
        ExactRatio value,
        IndicatorStatus status) {
    public ResultComponent {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        Objects.requireNonNull(status, "status");
        if (numerator.signum() < 0 || denominator.signum() < 0 || numerator.compareTo(denominator) > 0) {
            throw new IllegalArgumentException(
                    "component " + code + " needs 0 <= numerator <= denominator, got " + numerator + "/" + denominator);
        }
    }

    /** Counts a component exactly; an empty denominator yields {@code NO_DENOMINATOR}, not zero. */
    public static ResultComponent of(ComponentSpec spec, BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            return new ResultComponent(
                    spec.code(),
                    spec.kind(),
                    spec.weight(),
                    numerator,
                    denominator,
                    null,
                    IndicatorStatus.NO_DENOMINATOR);
        }
        return new ResultComponent(
                spec.code(),
                spec.kind(),
                spec.weight(),
                numerator,
                denominator,
                new ExactRatio(numerator, denominator),
                IndicatorStatus.COMPUTED);
    }
}
