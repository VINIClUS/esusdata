package esusdata.indicator.reconciliation;

/**
 * How the references of a set decide the Portão D of its rule (spec §14). There is one policy: no
 * alternative reference ever stands in for one that failed.
 */
public enum SelectionPolicy {
    /** Every required reference must pass; one failure fails D, one pending keeps it pending. */
    ALL_REQUIRED
}
