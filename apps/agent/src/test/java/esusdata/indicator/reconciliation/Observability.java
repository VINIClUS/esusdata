package esusdata.indicator.reconciliation;

/**
 * How much of what a probe asks about the canonical dataset it could observe (spec §9.4). Only
 * {@link #NONE} lacks counts: an unobservable probe is not a probe that found zero.
 */
public enum Observability {
    /** Every subject the other reading concerns was observable: the counts are exact. */
    COMPLETE,
    /** Only part of them was: the counts are lower bounds, and the reason says what was missing. */
    PARTIAL,
    /** None was: there are no counts at all, and the reason says why. */
    NONE
}
