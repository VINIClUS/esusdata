package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;

/**
 * Which pre-registered references a run uses (spec §10). The answer comes from the declarations
 * alone: there is no choice by date, no "latest published", no fallback from a reference that
 * failed to one that passes, and no sorting, so the order is the order of declaration.
 */
public final class ReferenceSelector {

    private ReferenceSelector() {}

    /** The {@code DIAGNOSTIC} declarations of the set, in declaration order. */
    public static List<ReferenceDeclaration> diagnostics(ReferenceSet set) {
        return set.references().stream()
                .filter(declaration -> declaration.purpose() == ReferencePurpose.DIAGNOSTIC)
                .toList();
    }

    /**
     * Every reference D needs for this set: all of its {@code GATE} declarations, which the rules make
     * {@code ACTIVE} and required, in declaration order. A {@code GATE} declaration that breaks any
     * rule of {@link ReferenceDeclaration#violations()} (it is not required, not {@code ACTIVE}, or
     * has no {@code EXACT} or {@code EQUIVALENT_FOR_REFERENCE} dossier with its hash) is never skipped
     * silently: a gate set that dropped a reference could pass without it.
     *
     * @throws IllegalStateException if a {@code GATE} declaration is not fit to decide D
     */
    public static List<ReferenceDeclaration> requiredGateReferences(ReferenceSet set) {
        List<ReferenceDeclaration> gate = new ArrayList<>();
        for (ReferenceDeclaration declaration : set.references()) {
            if (declaration.purpose() == ReferencePurpose.GATE) {
                requireFitForGate(set, declaration);
                gate.add(declaration);
            }
        }
        return List.copyOf(gate);
    }

    private static void requireFitForGate(ReferenceSet set, ReferenceDeclaration declaration) {
        List<String> violations = declaration.violations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("GATE reference " + declaration.referenceId() + " of " + set.ruleVersion()
                    + " cannot decide D: " + String.join("; ", violations));
        }
    }
}
