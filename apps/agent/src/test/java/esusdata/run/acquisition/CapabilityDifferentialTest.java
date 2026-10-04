package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.run.acquisition.CapabilityDifferential.Comparison;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The comparison of the Rust × JDBC differential tests must itself notice a difference. */
class CapabilityDifferentialTest {

    @Test
    void sameRowsInAnotherOrderAgree() {
        Comparison comparison =
                CapabilityDifferential.compare("citizen", List.of("a", "c", "b", "b"), List.of("b", "a", "b", "c"));

        assertThat(comparison.agrees()).isTrue();
        assertThat(comparison.rustHash()).isEqualTo(comparison.jdbcHash());
    }

    @Test
    void aMissingExtraOrRepeatedRowIsCountedOnItsSide() {
        Comparison comparison =
                CapabilityDifferential.compare("citizen", List.of("a", "b", "b", "x"), List.of("a", "b", "z", "y"));

        assertThat(comparison.agrees()).isFalse();
        assertThat(comparison.rustOnly()).isEqualTo(2);
        assertThat(comparison.jdbcOnly()).isEqualTo(2);
        assertThat(comparison.rustHash()).isNotEqualTo(comparison.jdbcHash());
    }

    @Test
    void anEmptySideAgainstARowIsADifference() {
        Comparison comparison = CapabilityDifferential.compare("citizen", List.of(), List.of("a"));

        assertThat(comparison.agrees()).isFalse();
        assertThat(comparison.jdbcOnly()).isEqualTo(1);
    }
}
