package esusdata.indicator.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * One practice or subgroup as the ficha declares it — code (A, B, …), the ficha's own wording,
 * its weight in points and the window in the ficha's words. Descriptive only: the pack's rule
 * decides what satisfies it.
 */
public record ComponentSpec(String code, String label, ComponentKind kind, BigInteger weight, String window) {
    public ComponentSpec {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(weight, "weight");
        if (weight.signum() < 0) {
            throw new IllegalArgumentException("weight must not be negative: " + weight);
        }
    }

    public static ComponentSpec practice(String code, String label, long weight, String window) {
        return new ComponentSpec(code, label, ComponentKind.PRACTICE, BigInteger.valueOf(weight), window);
    }

    public static ComponentSpec subgroup(String code, String label, long weight, String window) {
        return new ComponentSpec(code, label, ComponentKind.SUBGROUP, BigInteger.valueOf(weight), window);
    }
}
