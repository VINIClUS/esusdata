package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import java.util.EnumMap;
import java.util.Map;

/**
 * The tables of NT nº 8/2026-DEAPS/SAPS/MS the Nota Final uses, verbatim (transcription in {@code
 * docs/metodologia/componente-iii-nt08-2026.md}). The weights 1/2/2/1/1/1/2 (Quadro 2, pp. 2–3) live
 * in {@link ComponentIII#DESCRIPTOR}, one per component indicator.
 */
public final class Nt08Tables {

    /**
     * NT 8/2026, item 4.3.2 (p. 3): "cada conceito obtido no indicador equivale a pontuação abaixo:
     * Regular 0,25 · Suficiente 0,50 · Bom 0,75 · Ótimo 1,00".
     */
    private static final Map<Classification, ExactRatio> FACTORS = factors();

    private static final ExactRatio FIVE = ExactRatio.of(5, 1);

    private Nt08Tables() {}

    /** The factor of a concept (item 4.3.2, p. 3), exact. */
    public static ExactRatio factor(Classification concept) {
        return FACTORS.get(concept);
    }

    /**
     * Quadro 6 (p. 4), "Classificação para o Incentivo Financeiro conforme a Nota Final do
     * Componente III": {@code > 7,5 Ótimo · ≥ 5 e ≤ 7,5 Bom · > 2,5 e < 5 Suficiente · ≤ 2,5
     * Regular}. Bom is closed at 5 and Suficiente open there, so these are not {@code (lower, upper]}
     * {@link esusdata.indicator.model.Bands}; decided by cross-multiplication, never rounded first
     * (MET-36, AMB-CIII-04).
     */
    public static Classification classifyFinalScore(ExactRatio score) {
        if (score.compareToFraction(15, 2) > 0) {
            return Classification.OTIMO;
        }
        if (score.compareTo(FIVE) >= 0) {
            return Classification.BOM;
        }
        if (score.compareToFraction(5, 2) > 0) {
            return Classification.SUFICIENTE;
        }
        return Classification.REGULAR;
    }

    private static Map<Classification, ExactRatio> factors() {
        Map<Classification, ExactRatio> factors = new EnumMap<>(Classification.class);
        factors.put(Classification.REGULAR, ExactRatio.of(1, 4));
        factors.put(Classification.SUFICIENTE, ExactRatio.of(1, 2));
        factors.put(Classification.BOM, ExactRatio.of(3, 4));
        factors.put(Classification.OTIMO, ExactRatio.of(1, 1));
        return factors;
    }
}
