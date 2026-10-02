package esusdata.indicator.model;

/**
 * What a result's value means (ADR 0030). The unit decides how the value is formed, so it is part
 * of the result rather than something a screen infers from the pack id.
 */
public enum ValueKind {
    /** C1: {@code 100 × numerator / denominator}, an exact percentage. */
    PERCENTAGE,
    /**
     * C2–C6: mean points per eligible person or episode, already on the 0–100 scale. Never
     * multiplied by 100 again (Tech Spec §2.4: "não multiplicar novamente o resultado por 100").
     */
    SCORE,
    /** C7: {@code Σ weight × n/d} over subpopulations with their own denominators, 0–100. */
    COMPOSITE_SCORE,
    /** Nota Final do Componente III: {@code Σ weight × factor}, 0–10 (NT 8/2026). */
    FINAL_SCORE
}
