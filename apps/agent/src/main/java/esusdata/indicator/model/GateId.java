package esusdata.indicator.model;

/**
 * The release gates of a result (ADR 0032, which amends Tech Spec §4.4: Portão E is gone and
 * A–D are automatic checks, never a person's sign-off).
 *
 * <ul>
 *   <li>A — the official ficha was checked against the transcription, with no methodological
 *       change pending (read from the registry);
 *   <li>B — no blocking standing limitation or open ambiguity (evaluated on every result);
 *   <li>C — every capability the pack reads is {@code VALIDATED} for the source (evaluated on
 *       every result);
 *   <li>D — reconciled with the public SIAPS reference within tolerance (read from the registry).
 * </ul>
 */
public enum GateId {
    A("Portão A (fonte e vigência)"),
    B("Portão B (modelo de cálculo)"),
    C("Portão C (adaptador)"),
    D("Portão D (reconciliação)");

    private final String label;

    GateId(String label) {
        this.label = label;
    }

    /** The gate as people read it, e.g. {@code Portão A (fonte e vigência)}. */
    public String label() {
        return label;
    }

    /** What a result says while this gate has not passed. */
    public String incompleteReason() {
        return label + " incompleto";
    }

    /** Whether the registry file carries this gate (A and D); B and C are checked per result. */
    public boolean isRegistered() {
        return this == A || this == D;
    }
}
