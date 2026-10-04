package esusdata.indicator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The five release gates of Tech Spec §4.4 (Portões A–E). They are supplied by the release
 * workflow, never inferred from the presence of a rule class or of a compatibility entry: a result
 * must not look published while any gate is incomplete. Every pack declares its own (ADR 0030);
 * standing limitations of a pack are checked by the pack itself, next to these gates.
 */
public record ReleaseGates(
        boolean sourceAndValidity,
        boolean calculationModel,
        boolean adapter,
        boolean reconciliation,
        boolean pilotAndOperations) {

    public static ReleaseGates allComplete() {
        return new ReleaseGates(true, true, true, true, true);
    }

    /** C1 today: the adapter is validated live (Portão C); A, B, D and E are not. */
    public static ReleaseGates adapterOnly() {
        return new ReleaseGates(false, false, true, false, false);
    }

    /**
     * A pack whose ficha is transcribed but not reviewed by the team and whose capabilities are
     * still {@code NOT_TESTED} — every C2–C7 pack when it is first added (ADR 0030).
     */
    public static ReleaseGates noneComplete() {
        return new ReleaseGates(false, false, false, false, false);
    }

    public boolean isComplete() {
        return sourceAndValidity && calculationModel && adapter && reconciliation && pilotAndOperations;
    }

    public List<String> incompleteReasons() {
        List<String> reasons = new ArrayList<>();
        if (!sourceAndValidity) {
            reasons.add("Portão A (fonte e vigência) incompleto");
        }
        if (!calculationModel) {
            reasons.add("Portão B (modelo de cálculo) incompleto");
        }
        if (!adapter) {
            reasons.add("Portão C (adaptador) incompleto");
        }
        if (!reconciliation) {
            reasons.add("Portão D (reconciliação) incompleto");
        }
        if (!pilotAndOperations) {
            reasons.add("Portão E (piloto e operação) incompleto");
        }
        return List.copyOf(reasons);
    }
}
