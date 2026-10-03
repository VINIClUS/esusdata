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
 *
 * <p>No answer before Q1/2026: § 2º counts the transition "a contar da primeira parcela de custeio
 * desta nova metodologia", a date this pack does not transcribe, and quadrimestres before 2026 were
 * consolidated under NT nº 6/2025 (revoked by NT 8/2026, p. 4), not under the rule written here.
 */
public final class FinancialTransition {

    private static final Quadrimestre FIRST_COVERED = new Quadrimestre(2026, 1);
    private static final Quadrimestre DERIVED = new Quadrimestre(2026, 3);
    private static final Quadrimestre METHODOLOGICAL_FROM = new Quadrimestre(2027, 1);

    private FinancialTransition() {}

    /** True from Q1/2026 on: the quadrimestres this transition answers for. */
    public static boolean covers(Quadrimestre quadrimestre) {
        return quadrimestre.compareTo(FIRST_COVERED) >= 0;
    }

    /**
     * The classification the transfer of {@code quadrimestre} uses for a methodological one.
     *
     * @throws IllegalArgumentException before Q1/2026 ({@link #covers})
     */
    public static Classification classify(Quadrimestre quadrimestre, Classification methodological) {
        if (!covers(quadrimestre)) {
            throw new IllegalArgumentException("transição financeira não transcrita antes de " + FIRST_COVERED);
        }
        if (quadrimestre.equals(FIRST_COVERED)) {
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
