package esusdata.result.dto;

import esusdata.indicator.model.ExactRatio;

/**
 * An exact fraction as canonical integer strings (§1.7.1, ADR 0030); the decimal beside it is
 * derived for display only.
 */
public record ExactValue(String numerator, String denominator) {

    /** The fraction, or {@code null} for a value that does not exist. */
    public static ExactValue of(ExactRatio value) {
        return value == null
                ? null
                : new ExactValue(
                        value.numerator().toString(), value.denominator().toString());
    }
}
