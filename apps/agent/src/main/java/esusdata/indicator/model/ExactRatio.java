package esusdata.indicator.model;

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
public record ExactRatio(BigInteger numerator, BigInteger denominator) implements Comparable<ExactRatio> {

    public ExactRatio {
        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException("denominator must be positive, got " + denominator);
        }
    }

    public static ExactRatio of(long numerator, long denominator) {
        return new ExactRatio(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static ExactRatio of(BigInteger numerator, BigInteger denominator) {
        return new ExactRatio(numerator, denominator);
    }

    public static ExactRatio zero() {
        return new ExactRatio(BigInteger.ZERO, BigInteger.ONE);
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    /** {@code this + other}, exact; the result is not reduced. */
    public ExactRatio plus(ExactRatio other) {
        return new ExactRatio(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    /** {@code this × factor}, exact — weights in points (C2–C7) and in the Nota Final (NT 8/2026). */
    public ExactRatio times(BigInteger factor) {
        return new ExactRatio(numerator.multiply(factor), denominator);
    }

    /** {@code this × other}, exact — a weight that is itself a fraction (the 0,25 … 1,00 factors). */
    public ExactRatio times(ExactRatio other) {
        return new ExactRatio(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    /**
     * Orders two ratios by cross-multiplication, never by decimal conversion. Equal values with
     * different representations ({@code 1/2} and {@code 2/4}) compare as 0; {@link #equals} still
     * compares the components, as a record does.
     */
    @Override
    public int compareTo(ExactRatio other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
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

    /** Average of several exact ratios, preserving precision — never rounds intermediate values (MET-33). */
    public static ExactRatio meanOfExactRatios(ExactRatio... ratios) {
        if (ratios.length == 0) {
            throw new IllegalArgumentException("at least one ratio is required to compute a mean");
        }
        BigInteger sumNumerator = BigInteger.ZERO;
        BigInteger lcm = BigInteger.ONE;
        for (ExactRatio r : ratios) {
            lcm = lcm.multiply(r.denominator).divide(gcd(lcm, r.denominator));
        }
        for (ExactRatio r : ratios) {
            BigInteger factor = lcm.divide(r.denominator);
            sumNumerator = sumNumerator.add(r.numerator.multiply(factor));
        }
        return new ExactRatio(sumNumerator, lcm.multiply(BigInteger.valueOf(ratios.length)));
    }

    private static BigInteger gcd(BigInteger a, BigInteger b) {
        return b.signum() == 0 ? a : gcd(b, a.mod(b));
    }

    /**
     * Explicit, final, display-only decimal conversion. Never used in a classification decision
     * — only for rendering (§1.7.1: "duas casas, arredondamento HALF_UP, exclusivamente na
     * exibição").
     */
    public BigDecimal toScaledBigDecimal(int scale) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, RoundingMode.HALF_UP);
    }
}
