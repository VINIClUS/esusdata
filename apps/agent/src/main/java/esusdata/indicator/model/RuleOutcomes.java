package esusdata.indicator.model;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import java.util.ArrayList;
import java.util.List;

/** Shared steps every rule applies to its computed result (ADR 0030). */
public final class RuleOutcomes {

    private RuleOutcomes() {}

    /**
     * Applies the release gates (§4.4): while the pack's execution is not enabled, a computed
     * result keeps its exact counts and components but loses its value and classification, and
     * says why. A result that was not computed ({@code NO_DENOMINATOR}, {@code RULE_AMBIGUITY},
     * {@code UNSUPPORTED_SOURCE}) keeps its own status — the gate is added to its limitations.
     */
    public static IndicatorResult gate(PackDescriptor descriptor, IndicatorResult computed) {
        if (descriptor.executionEnabled()) {
            return computed;
        }
        List<String> limitations = new ArrayList<>(computed.limitations());
        for (String reason : descriptor.blockedGates()) {
            if (!limitations.contains(reason)) {
                limitations.add(reason);
            }
        }
        boolean wasComputed = computed.status() == IndicatorStatus.COMPUTED;
        return new IndicatorResult(
                wasComputed ? IndicatorStatus.BLOCKED : computed.status(),
                wasComputed ? null : computed.valueText(),
                computed.numerator(),
                computed.denominator(),
                computed.denominatorKind(),
                wasComputed ? null : computed.classification(),
                computed.referencePeriod(),
                computed.ruleVersion(),
                computed.dataCutoff(),
                computed.municipalityIbge(),
                limitations,
                computed.calculationPolicyVersion(),
                computed.valueKind(),
                wasComputed ? null : computed.valueExact(),
                computed.components(),
                computed.consolidationEligible());
    }

    /**
     * What a pack returns while its rule is still being written: no counts, the reason and the
     * gates in the limitations, and {@code BLOCKED} — never a zero (ADR 0030).
     */
    public static RuleOutcome pending(PackDescriptor descriptor, EvaluationContext context, String reason) {
        List<String> limitations = new ArrayList<>();
        limitations.add(reason);
        limitations.addAll(descriptor.standingLimitations());
        limitations.addAll(descriptor.blockedGates());
        IndicatorResult result = new IndicatorResult(
                IndicatorStatus.BLOCKED,
                null,
                null,
                null,
                descriptor.denominatorKind(),
                null,
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations.stream().distinct().toList(),
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                null,
                List.of(),
                false);
        return new RuleOutcome(result, List.of(), List.of());
    }

    /** {@link #gate} applied to the municipal result and to every team result. */
    public static RuleOutcome gate(PackDescriptor descriptor, RuleOutcome computed) {
        List<TeamResult> teams = computed.teams().stream()
                .map(t -> new TeamResult(t.ine(), t.cnes(), gate(descriptor, t.result())))
                .toList();
        return new RuleOutcome(gate(descriptor, computed.result()), teams, computed.evidence());
    }
}
