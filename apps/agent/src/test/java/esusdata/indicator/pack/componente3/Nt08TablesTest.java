package esusdata.indicator.pack.componente3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import org.junit.jupiter.api.Test;

/**
 * NT 8/2026 item 4.3.2 (conceito → pontuação) and Quadro 6 (Nota Final → classificação), as
 * transcribed in {@code docs/metodologia/componente-iii-nt08-2026.md}. Boundaries are decided on the
 * exact value, never on a rounded one (AMB-CIII-04, ENG-25).
 */
class Nt08TablesTest {

    private static final long MILLION = 1_000_000;

    /** {@code whole/2} plus {@code delta} millionths — e.g. half(5, 1) is 2,500001. */
    private static ExactRatio half(long whole, long deltaMillionths) {
        return ExactRatio.of(whole * MILLION / 2 + deltaMillionths, MILLION);
    }

    // ---- item 4.3.2: Regular 0,25 · Suficiente 0,50 · Bom 0,75 · Ótimo 1,00 ----
    @Test
    void item432_factorOfEachConcept() {
        assertThat(Nt08Tables.factor(Classification.REGULAR)).isEqualByComparingTo(ExactRatio.of(1, 4));
        assertThat(Nt08Tables.factor(Classification.SUFICIENTE)).isEqualByComparingTo(ExactRatio.of(1, 2));
        assertThat(Nt08Tables.factor(Classification.BOM)).isEqualByComparingTo(ExactRatio.of(3, 4));
        assertThat(Nt08Tables.factor(Classification.OTIMO)).isEqualByComparingTo(ExactRatio.of(1, 1));
    }

    // ---- MET-36: 7,5 is Bom ("≥ 5 e ≤ 7,5"); 7,5001 is Ótimo ("> 7,5"), no rounding first ----
    @Test
    void met36_finalScore7_5IsBomAnd7_5001IsOtimo() {
        assertThat(Nt08Tables.classifyFinalScore(ExactRatio.of(15, 2))).isEqualTo(Classification.BOM);
        assertThat(Nt08Tables.classifyFinalScore(ExactRatio.of(75_001, 10_000))).isEqualTo(Classification.OTIMO);
    }

    // ---- ENG-25 on Quadro 6: 2,5 / 5 / 7,5 exact and ±1/10^6 ----
    @Test
    void eng25_quadro6BoundariesAreExact() {
        // ≤ 2,5 Regular; > 2,5 e < 5 Suficiente
        assertThat(Nt08Tables.classifyFinalScore(half(5, -1))).isEqualTo(Classification.REGULAR);
        assertThat(Nt08Tables.classifyFinalScore(half(5, 0))).isEqualTo(Classification.REGULAR);
        assertThat(Nt08Tables.classifyFinalScore(half(5, 1))).isEqualTo(Classification.SUFICIENTE);
        // ≥ 5 e ≤ 7,5 Bom
        assertThat(Nt08Tables.classifyFinalScore(half(10, -1))).isEqualTo(Classification.SUFICIENTE);
        assertThat(Nt08Tables.classifyFinalScore(half(10, 0))).isEqualTo(Classification.BOM);
        assertThat(Nt08Tables.classifyFinalScore(half(10, 1))).isEqualTo(Classification.BOM);
        // > 7,5 Ótimo
        assertThat(Nt08Tables.classifyFinalScore(half(15, -1))).isEqualTo(Classification.BOM);
        assertThat(Nt08Tables.classifyFinalScore(half(15, 0))).isEqualTo(Classification.BOM);
        assertThat(Nt08Tables.classifyFinalScore(half(15, 1))).isEqualTo(Classification.OTIMO);
        // extremes of the attainable range (all Regular / all Ótimo), with an unreduced representation
        assertThat(Nt08Tables.classifyFinalScore(ExactRatio.of(10, 4))).isEqualTo(Classification.REGULAR);
        assertThat(Nt08Tables.classifyFinalScore(ExactRatio.of(40, 4))).isEqualTo(Classification.OTIMO);
    }
}
