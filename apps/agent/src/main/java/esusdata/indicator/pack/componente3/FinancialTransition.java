package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.Quadrimestre;

/**
 * The financial classification of the Componente III in the transition of Portaria GM/MS nº
 * 10.994/2026 (art. 1º, new wording of art. 3º of Portaria GM/MS nº 3.493/2024), kept apart from the
 * methodological one (Tech Spec §2.4). "Quadrimestre" is read as the evaluated one, as MET-38 does
 * (AMB-CIII-09).
 *
 * <ul>
 *   <li>up to Q1/2026 — art. 3º, II: "até o primeiro quadrimestre de 2026, considerando os valores
 *       da classificação "bom"";
 *   <li>Q2/2026 — § 3º, I and II: "ótimo" keeps "ótimo"; "bom", "suficiente" and "regular" receive
 *       "bom";
 *   <li>Q3/2026 — the same partial regime, <b>derived</b> from § 3º ("iniciará" in Q2/2026) and
 *       § 6º; the Portaria does not name it for the Componente III (AMB-CIII-10);
 *   <li>from Q1/2027 — § 6º: the methodological classification.
 * </ul>
 */
public final class FinancialTransition {

    private static final Quadrimestre FIXED_BOM_UNTIL = new Quadrimestre(2026, 1);
    private static final Quadrimestre DERIVED = new Quadrimestre(2026, 3);
    private static final Quadrimestre METHODOLOGICAL_FROM = new Quadrimestre(2027, 1);

    private FinancialTransition() {}

    /** The classification the transfer of {@code quadrimestre} uses for a methodological one. */
    public static Classification classify(Quadrimestre quadrimestre, Classification methodological) {
        if (quadrimestre.compareTo(FIXED_BOM_UNTIL) <= 0) {
            return Classification.BOM;
        }
        if (quadrimestre.compareTo(METHODOLOGICAL_FROM) >= 0) {
            return methodological;
        }
        return methodological == Classification.OTIMO ? Classification.OTIMO : Classification.BOM;
    }

    /** True when the rule for {@code quadrimestre} is derived, not literal (Q3/2026, AMB-CIII-10). */
    public static boolean isDerived(Quadrimestre quadrimestre) {
        return quadrimestre.equals(DERIVED);
    }
}
