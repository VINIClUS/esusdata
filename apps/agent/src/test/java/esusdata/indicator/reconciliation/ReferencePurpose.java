package esusdata.indicator.reconciliation;

/** What a declared SIAPS reference is for (spec §6.2). */
public enum ReferencePurpose {
    /** Produces a comparison and a dossier; never writes the Portão D. */
    DIAGNOSTIC,
    /** Takes part in the verdict of the Portão D, once pre-registered with a compatible dossier. */
    GATE
}
