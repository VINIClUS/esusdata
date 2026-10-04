package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ENG-25 over the C2–C7 bands (fichas Q02–Q07, item 30): exact edges, no display rounding. */
class BandsTest {

    private static Classification band(long numerator, long denominator) {
        return Bands.QUALIDADE_C2_C7
                .classify(ExactRatio.of(numerator, denominator))
                .orElseThrow();
    }

    @Test
    void eng25_edgesBelongToTheLowerBandAndAMillionthAboveMovesUp() {
        assertThat(band(0, 1)).isEqualTo(Classification.REGULAR);
        assertThat(band(25, 1)).isEqualTo(Classification.REGULAR);
        assertThat(band(25_000_001, 1_000_000)).isEqualTo(Classification.SUFICIENTE);
        assertThat(band(50, 1)).isEqualTo(Classification.SUFICIENTE);
        assertThat(band(50_000_001, 1_000_000)).isEqualTo(Classification.BOM);
        assertThat(band(75, 1)).isEqualTo(Classification.BOM);
        assertThat(band(75_000_001, 1_000_000)).isEqualTo(Classification.OTIMO);
        assertThat(band(100, 1)).isEqualTo(Classification.OTIMO);
    }

    @Test
    void aNonTerminatingFractionIsDecidedExactly() {
        // 226/3 = 75.333… — above 75 without ever forming a decimal
        assertThat(band(226, 3)).isEqualTo(Classification.OTIMO);
        // 225/3 = 75 exactly
        assertThat(band(225, 3)).isEqualTo(Classification.BOM);
    }

    @Test
    void aValueOutsideTheFichaScaleHasNoBand() {
        assertThat(Bands.QUALIDADE_C2_C7.classify(ExactRatio.of(100_000_001, 1_000_000)))
                .isEmpty();
    }
}
