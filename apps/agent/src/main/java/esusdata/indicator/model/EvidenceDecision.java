package esusdata.indicator.model;

/**
 * Why an evidence row exists. The first three are C1's and keep their V2 meaning; the rest are
 * per-person and per-practice decisions of C2–C7 (ADR 0030). An exclusion always carries a reason
 * code so the population can be rebuilt (ENG-36).
 */
public enum EvidenceDecision {
    IN_NUMERATOR,
    DENOMINATOR_ONLY,
    EXCLUDED_UNMAPPED,
    /** The subject is in the denominator. */
    ELIGIBLE,
    /** The subject was considered and left out (interruption, age, link, exclusion rule). */
    EXCLUDED,
    PRACTICE_MET,
    PRACTICE_NOT_MET,
    /** The ficha scores the practice without evidence (e.g. visits for eAP tipo 76). */
    PRACTICE_EXEMPT,
    /**
     * The practice could not be decided for this subject because the ficha is ambiguous for the
     * case at hand (an {@code AMB-…} reading): no points (never 0) and a reason code that names the
     * ambiguity. Never folded into {@link #PRACTICE_NOT_MET}.
     */
    PRACTICE_AMBIGUOUS,
    /** A source event that supports a practice decision of the same subject. */
    SUPPORTING_EVENT
}
