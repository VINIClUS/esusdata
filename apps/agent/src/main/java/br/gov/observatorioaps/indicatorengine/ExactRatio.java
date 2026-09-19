package br.gov.observatorioaps.indicatorengine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * An exact rational number over {@link BigInteger}, per Tech Spec §1.7.1: "a decisão de faixa usa
 * comparação exata de frações com inteiros de precisão arbitrária (BigInteger)... Não arredondar
 * resultados mensais antes da consolidação." {@code double}/{@code float} never appear in this
 * class (ADR 0005).
 *
 * <p>Band-boundary comparisons ({@link #compareToFraction}) are decided by cross-multiplication
 * of integers — {@code a/b ⋛ x/y ⟺ a·y ⋛ x·b} for positive denominators — never by converting
 * either side to a decimal first. Decimal conversion ({@link #toScaledBigDecimal}) is an explicit,
 * final, display-only operation.
 */
public record ExactRatio(BigInteger numerator, BigInteger denominator) {

    public ExactRatio {
        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException("denominator must be positive, got " + denominator);
        }
    }

    public static ExactRatio of(long numerator, long denominator) {
        return new ExactRatio(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static ExactRatio zero() {
        return new ExactRatio(BigInteger.ZERO, BigInteger.ONE);
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    /** As a percentage ratio (this × 100), still exact — used before comparing to a 0-100 band. */
    public ExactRatio asPercentage() {
        return new ExactRatio(numerator.multiply(BigInteger.valueOf(100)), denominator);
    }

    /**
     * Compares this ratio to {@code boundNumerator/boundDenominator} without ever forming a
     * decimal. Returns a value {@literal <0}, {@literal =0} or {@literal >0} exactly like
     * {@link Comparable#compareTo}.
     */
    public int compareToFraction(long boundNumerator, long boundDenominator) {
        if (boundDenominator <= 0) {
            throw new IllegalArgumentException("boundDenominator must be positive");
        }
        BigInteger left = numerator.multiply(BigInteger.valueOf(boundDenominator));
        BigInteger right = BigInteger.valueOf(boundNumerator).multiply(denominator);
        return left.compareTo(right);
    }

    /** Sum of several exact ratios that already share intent as "percentages to average" (MET-33). */
    public static ExactRatio meanOfIntegerPercentages(long... monthlyPercentages) {
        BigInteger sum = BigInteger.ZERO;
        for (long p : monthlyPercentages) {
            sum = sum.add(BigInteger.valueOf(p));
        }
        return new ExactRatio(sum, BigInteger.valueOf(monthlyPercentages.length));
    }

    /**
     * Explicit, final, display-only decimal conversion. Never used in a classification decision
     * — only for rendering (§1.7.1: "duas casas, arredondamento HALF_UP, exclusivamente na
     * exibição").
     */
    public BigDecimal toScaledBigDecimal(int scale) {
        return new BigDecimal(numerator)
                .divide(new BigDecimal(denominator), scale, RoundingMode.HALF_UP);
    }
}
