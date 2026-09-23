package esusdata.indicator.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorResultTest {

    @Test
    void copiesLimitationsAndExposesThemAsUnmodifiable() {
        List<String> input = new ArrayList<>(List.of("original"));
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED,
                "100.0000",
                BigInteger.ONE,
                BigInteger.ONE,
                "TEST",
                Classification.REGULAR,
                "2026-03",
                "test-rule",
                "2026-03-31",
                "3541307",
                input,
                "test-policy");

        input.add("added after construction");

        assertThat(result.limitations()).containsExactly("original");
        assertThatThrownBy(() -> result.limitations().add("mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
