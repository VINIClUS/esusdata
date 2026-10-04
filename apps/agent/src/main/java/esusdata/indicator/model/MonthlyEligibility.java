package esusdata.indicator.model;

/**
 * Which monthly results enter the quadrimestral mean (NT 8/2026, items 4.1–4.1.1). A month that
 * does not enter is left out, never turned into zero.
 */
public enum MonthlyEligibility {
    /** Every monitored month counts (C1, C4–C7). */
    ALL_MONTHS,
    /**
     * Only months with the cohort event the NT names: a child completing two years (C2), a
     * pregnancy reaching the 42nd day of puerperium (C3). The pack sets {@code
     * IndicatorResult.consolidationEligible} per month.
     */
    MONTHS_WITH_COHORT_EVENT
}
