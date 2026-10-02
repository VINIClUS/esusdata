package esusdata.indicator.model;

/** A part of a composite result (ADR 0030). */
public enum ComponentKind {
    /** A good practice scored per person or episode (C2–C6). */
    PRACTICE,
    /** A subpopulation with its own denominator and weight (C7). */
    SUBGROUP,
    /** A whole indicator weighted into a composite score (C1–C7 in the Nota Final, NT 8/2026). */
    INDICATOR
}
