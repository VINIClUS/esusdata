package esusdata.indicator;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateChecks;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Synthetic gate state for tests that need a pack released: the real registry has every gate
 * pending, so a test that wants a {@code COMPUTED} result builds its own (ADR 0032). Never used by
 * production code.
 */
public final class TestGates {

    /** The SHA-256 of nothing in particular: the registry checks only the shape of evidence. */
    public static final String SHA = "0".repeat(64);

    private TestGates() {}

    /** A registry in which Portões A and D have passed for exactly these compiled packs. */
    public static ReleaseGateRegistry registryPassing(PackDescriptor... descriptors) {
        String packs = Arrays.stream(descriptors)
                .map(d -> """
                        {"pack":"%s","rule_version":"%s","blocking_gaps_closed":[],"gates":{"A":%s,"D":%s}}
                        """.formatted(
                        d.id(), d.ruleVersion(), passed("conferencia-fichas@1"), passed("reconciliacao-siaps@1")))
                .collect(Collectors.joining(","));
        return ReleaseGateRegistry.fromJson(
                "{\"schema_version\":\"1\",\"packs\":[" + packs + "]}", List.of(descriptors));
    }

    private static String passed(String check) {
        return """
                {"status":"PASSED","check":"%s","checked_at":"2026-10-06","evidence":[{"kind":"doc","ref":"docs/x.md","sha256":"%s"}]}
                """.formatted(check, SHA);
    }

    /**
     * The gates as this release ships them for the pack: A and D from the registry (all pending), B
     * from its limitations, C passing (a source that validates every capability).
     */
    public static GateStatus shipped(PackDescriptor descriptor) {
        return ReleaseGateRegistry.bundled()
                .statusOf(descriptor)
                .withEvaluated(GateChecks.calculationModel(descriptor, null), GateChecks.adapter(List.of(), null));
    }

    /**
     * What the executor publishes for {@code ungated}: the shipped registry, a source that validates
     * every capability the pack reads (Portão C passes).
     */
    public static RuleOutcome published(PackDescriptor descriptor, RuleOutcome ungated) {
        return RuleOutcomes.gate(shipped(descriptor), ungated);
    }

    /** A gate snapshot for a result staged by hand (V12 refuses a COMPUTED one without it). */
    public static String snapshot() {
        return ReleaseGateRegistry.snapshotJson(GateStatus.pending("test", "test@0.0.0", false));
    }

    /** Every gate A–D passed for {@code descriptor}. */
    public static GateStatus allPassed(PackDescriptor descriptor) {
        Map<GateId, GateCheck> gates = new EnumMap<>(GateId.class);
        for (GateId id : GateId.values()) {
            gates.put(id, new GateCheck(GateCheck.State.PASSED, "teste@1", "2026-10-06", List.of(), null));
        }
        return new GateStatus(descriptor.id(), descriptor.ruleVersion(), gates, false);
    }
}
