package esusdata.indicator.reconciliation;

/**
 * Whether a reference is methodologically compatible with a rule, as its compatibility dossier
 * decided it (spec §6.3). Never inferred from a date, and never from agreement between the local
 * and the official figures.
 */
public enum ReferenceCompatibility {
    /** No dossier yet. A dossier never ends here. */
    UNKNOWN,
    /** Same normative profile, every probe complete and without divergence. */
    EXACT,
    /** Known differences, all proven inactive for this revision and this local source. */
    EQUIVALENT_FOR_REFERENCE,
    /** A complete probe shows a difference that changes a decision, a class or a score. */
    INCOMPATIBLE,
    /** Evidence exists but the universe, a probe, the coverage or the identity is insufficient. */
    INCONCLUSIVE;

    /** True for the two verdicts that put a reference in the gate set. */
    public boolean authorizesGate() {
        return this == EXACT || this == EQUIVALENT_FOR_REFERENCE;
    }

    /** True once a dossier decided: everything but {@link #UNKNOWN}. */
    public boolean isDecided() {
        return this != UNKNOWN;
    }
}
