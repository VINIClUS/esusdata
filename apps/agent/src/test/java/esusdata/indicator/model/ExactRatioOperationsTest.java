package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** ADR 0030 arithmetic: sums, products and ordering stay exact fractions (ADR 0005). */
class ExactRatioOperationsTest {

    @Test
    void addsAndMultipliesWithoutRounding() {
        ExactRatio third = ExactRatio.of(1, 3);
        ExactRatio sum = third.plus(third).plus(third);
        assertThat(sum.compareTo(ExactRatio.of(1, 1))).isZero();
        assertThat(third.times(BigInteger.valueOf(3)).compareTo(ExactRatio.of(1, 1)))
                .isZero();
        assertThat(ExactRatio.of(3, 4).times(ExactRatio.of(2, 3)).compareTo(ExactRatio.of(1, 2)))
                .isZero();
    }

    @Test
    void reducesToLowestTermsWithoutChangingTheValue() {
        assertThat(ExactRatio.of(220, 4).reduced()).isEqualTo(ExactRatio.of(55, 1));
        assertThat(ExactRatio.of(0, 5).reduced()).isEqualTo(ExactRatio.of(0, 1));
        assertThat(ExactRatio.of(-6, 4).reduced()).isEqualTo(ExactRatio.of(-3, 2));
        ExactRatio lowest = ExactRatio.of(3, 7);
        assertThat(lowest.reduced()).isSameAs(lowest);
    }

    @Test
    void ordersByValueNotByRepresentation() {
        assertThat(ExactRatio.of(1, 2).compareTo(ExactRatio.of(2, 4))).isZero();
        assertThat(ExactRatio.of(1, 2)).isNotEqualTo(ExactRatio.of(2, 4));
        assertThat(ExactRatio.of(1, 3)).isLessThan(ExactRatio.of(334, 1000));
        assertThat(ExactRatio.of(BigInteger.TEN, BigInteger.ONE)).isGreaterThan(ExactRatio.of(9, 1));
    }
}
