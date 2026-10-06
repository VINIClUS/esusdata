package esusdata.indicator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Where the four release gates of one pack stand for the rule version compiled into this release
 * (ADR 0032). It replaces the boolean presets the packs used to carry. A and D come from the gate
 * registry — {@code PENDING} until an automated check passes them; B and C are evaluated on every
 * result ({@link GateChecks}). A check recorded against an older {@code rule_version} never counts:
 * {@code stale} says the registry only knew another version, so screens can say "aprovação anulada
 * por nova versão".
 *
 * @param stale true when the registry holds entries for this pack, but none for this rule version
 */
public record GateStatus(String pack, String ruleVersion, Map<GateId, GateCheck> gates, boolean stale) {

    public GateStatus {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        EnumMap<GateId, GateCheck> complete = new EnumMap<>(GateId.class);
        for (GateId id : GateId.values()) {
            complete.put(id, Objects.requireNonNull(gates.get(id), "gate " + id));
        }
        gates = Collections.unmodifiableMap(complete);
    }

    /** Every gate pending: what a pack without a passed entry for its version is. */
    public static GateStatus pending(String pack, String ruleVersion, boolean stale) {
        Map<GateId, GateCheck> gates = new EnumMap<>(GateId.class);
        for (GateId id : GateId.values()) {
            gates.put(id, GateCheck.pending(null));
        }
        return new GateStatus(pack, ruleVersion, gates, stale);
    }

    /** {@link #pending(String, String, boolean)} for a compiled pack, never stale. */
    public static GateStatus pending(PackDescriptor descriptor) {
        return pending(descriptor.id(), descriptor.ruleVersion(), false);
    }

    /** The registry's A and D, with B and C as this run, or this catalog read, found them. */
    public GateStatus withEvaluated(GateCheck calculationModel, GateCheck adapter) {
        Map<GateId, GateCheck> next = new EnumMap<>(gates);
        next.put(GateId.B, calculationModel);
        next.put(GateId.C, adapter);
        return new GateStatus(pack, ruleVersion, next, stale);
    }

    public GateCheck check(GateId gate) {
        return gates.get(gate);
    }

    public boolean isComplete() {
        return gates.values().stream().allMatch(GateCheck::isPassed);
    }

    /** One reason per gate that has not passed, in A–D order. */
    public List<String> incompleteReasons() {
        List<String> reasons = new ArrayList<>();
        for (GateId id : GateId.values()) {
            if (!gates.get(id).isPassed()) {
                reasons.add(id.incompleteReason());
            }
        }
        return List.copyOf(reasons);
    }
}
