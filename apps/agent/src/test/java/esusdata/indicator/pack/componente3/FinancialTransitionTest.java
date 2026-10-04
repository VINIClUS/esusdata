package esusdata.indicator.pack.componente3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.Quadrimestre;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Portaria GM/MS nº 10.994/2026, Componente III (eSF/eAP): the financial classification of the
 * transition, kept apart from the methodological one. "Quadrimestre" is the evaluated one
 * (AMB-CIII-09, Tech Spec MET-38/MET-39).
 */
class FinancialTransitionTest {

    private static Classification financial(String quadrimestre, Classification methodological) {
        return FinancialTransition.classify(Quadrimestre.parse(quadrimestre), methodological);
    }

    /** Asserts the financial classification of Regular, Suficiente, Bom and Ótimo, in that order. */
    private static void assertRegime(
            String quadrimestre,
            Classification regular,
            Classification suficiente,
            Classification bom,
            Classification otimo) {
        assertThat(financial(quadrimestre, Classification.REGULAR))
                .as(quadrimestre + " Regular")
                .isEqualTo(regular);
        assertThat(financial(quadrimestre, Classification.SUFICIENTE))
                .as(quadrimestre + " Suficiente")
                .isEqualTo(suficiente);
        assertThat(financial(quadrimestre, Classification.BOM))
                .as(quadrimestre + " Bom")
                .isEqualTo(bom);
        assertThat(financial(quadrimestre, Classification.OTIMO))
                .as(quadrimestre + " Ótimo")
                .isEqualTo(otimo);
    }

    private static void assertAlwaysBom(String quadrimestre) {
        assertRegime(quadrimestre, Classification.BOM, Classification.BOM, Classification.BOM, Classification.BOM);
    }

    private static void assertPartial(String quadrimestre) {
        assertRegime(quadrimestre, Classification.BOM, Classification.BOM, Classification.BOM, Classification.OTIMO);
    }

    private static void assertIntegral(String quadrimestre) {
        assertRegime(
                quadrimestre,
                Classification.REGULAR,
                Classification.SUFICIENTE,
                Classification.BOM,
                Classification.OTIMO);
    }

    // ---- FIN-Q1-2026: art. 3º, II — "bom" whatever the methodological band ----
    @Test
    void finQ1_2026_alwaysBom() {
        assertAlwaysBom("2026-Q1");
        assertThat(FinancialTransition.covers(Quadrimestre.parse("2026-Q1"))).isTrue();
    }

    // ---- § 2º: the transition counts from the first parcel of the new methodology — no answer before 2026 ----
    @Test
    void portaria10994Par2_noFinancialClassificationBefore2026() {
        for (String q : List.of("2025-Q3", "2025-Q1", "2019-Q1")) {
            Quadrimestre quadrimestre = Quadrimestre.parse(q);
            assertThat(FinancialTransition.covers(quadrimestre)).as(q).isFalse();
            assertThatThrownBy(() -> FinancialTransition.classify(quadrimestre, Classification.OTIMO))
                    .as(q)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- FIN-Q2-2026: § 3º — Ótimo keeps Ótimo; Bom, Suficiente, Regular receive Bom ----
    @Test
    void finQ2_2026_partialRegime() {
        assertPartial("2026-Q2");
        assertThat(FinancialTransition.isDerived(Quadrimestre.parse("2026-Q2"))).isFalse();
    }

    // ---- FIN-Q3-2026: same partial regime, derived from § 3º + § 6º (AMB-CIII-10) ----
    @Test
    void finQ3_2026_partialRegimeIsDerived() {
        assertPartial("2026-Q3");
        assertThat(FinancialTransition.isDerived(Quadrimestre.parse("2026-Q3"))).isTrue();
    }

    // ---- FIN-Q1-2027: § 6º — financial equals methodological from 2027 on ----
    @Test
    void finQ1_2027_financialEqualsMethodologicalFrom2027On() {
        assertIntegral("2027-Q1");
        assertIntegral("2027-Q3");
        assertIntegral("2028-Q2");
    }

    // ---- AMB-CIII-10: only 2026-Q3 is a derived regime; literal ones are not flagged ----
    @Test
    void ambCiii10_onlyQ3_2026IsDerived() {
        for (String q : new String[] {"2025-Q3", "2026-Q1", "2026-Q2", "2027-Q1", "2028-Q2"}) {
            assertThat(FinancialTransition.isDerived(Quadrimestre.parse(q)))
                    .as(q)
                    .isFalse();
        }
    }
}
