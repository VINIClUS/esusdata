package esusdata.indicator.reconciliation;

/** The state of one captured revision of a SIAPS reference (spec §6.4). */
public enum ReferenceStatus {
    ACTIVE,
    /** Replaced by a newer revision of the same reference (official drift). */
    SUPERSEDED,
    /** Withdrawn: it should not have been trusted. */
    RETRACTED
}
