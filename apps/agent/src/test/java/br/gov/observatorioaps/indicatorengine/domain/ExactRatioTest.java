package br.gov.observatorioaps.indicatorengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** ENG-25: "Valores imediatamente abaixo, iguais e acima dos limites mantêm a classificação... a
 * razão/média exata não depende de arredondamento da UI." */
class ExactRatioTest {

    @Test
    void compareToFractionIsExactAtTheExactBoundary() {
        // 70/100 == 70/100 -> equal, not decided by rounding
        ExactRatio r = ExactRatio.of(70, 1);
        assertThat(r.compareToFraction(70, 1)).isZero();
    }

    @Test
    void nonTerminatingRatioIsDecidedExactlyAcrossTheBoundary() {
        // 1/3 = 33.333...%, which as a raw ratio (not *100) compared to 33/100 must be > (since
        // 1/3 > 33/100 exactly: 100 > 99). This proves the comparison never touches a decimal
        // approximation of 1/3.
        ExactRatio oneThird = ExactRatio.of(1, 3);
        assertThat(oneThird.compareToFraction(33, 100)).isPositive();
        assertThat(oneThird.compareToFraction(34, 100)).isNegative();
    }

    @Test
    void toScaledBigDecimalIsDisplayOnlyAndDoesNotAffectComparison() {
        ExactRatio oneThird = ExactRatio.of(1, 3);
        // Display rounds to 0.33, but the exact comparison must still say 1/3 > 33/100.
        assertThat(oneThird.toScaledBigDecimal(2).toPlainString()).isEqualTo("0.33");
        assertThat(oneThird.compareToFraction(33, 100)).isPositive();
    }

    @Test
    void meanOfIntegerPercentagesIsExact() {
        // MET-33: 40, 50, 60, 70 -> mean 55, no rounding needed since the sum divides evenly.
        ExactRatio mean = ExactRatio.meanOfIntegerPercentages(40, 50, 60, 70);
        assertThat(mean.numerator()).isEqualTo(BigInteger.valueOf(220));
        assertThat(mean.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(mean.compareToFraction(55, 1)).isZero();
    }
}
