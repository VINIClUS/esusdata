package esusdata.indicator.model;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import java.util.ArrayList;
import java.util.List;

/** Shared steps every rule applies to its computed result (ADR 0030). */
public final class RuleOutcomes {

    private RuleOutcomes() {}

    /**
     * Applies the release gates (§4.4, ADR 0032) — the executor's job, in one place, for every pack:
     * while any gate has not passed, a computed result keeps its exact counts and components but loses its value and classification, and
     * says why. A result that was not computed ({@code NO_DENOMINATOR}, {@code RULE_AMBIGUITY},
     * {@code UNSUPPORTED_SOURCE}) keeps its own status — the gate is added to its limitations.
     */
    public static IndicatorResult gate(GateStatus gates, IndicatorResult computed) {
        if (gates.isComplete()) {
            return computed;
        }
        List<String> limitations = new ArrayList<>(computed.limitations());
        for (String reason : gates.incompleteReasons()) {
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
    public static RuleOutcome pending(
            PackDescriptor descriptor, GateStatus gates, EvaluationContext context, String reason) {
        List<String> limitations = new ArrayList<>();
        limitations.add(reason);
        limitations.addAll(descriptor.standingLimitations());
        limitations.addAll(gates.incompleteReasons());
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
    public static RuleOutcome gate(GateStatus gates, RuleOutcome computed) {
        List<TeamResult> teams = computed.teams().stream()
                .map(t -> new TeamResult(t.ine(), t.cnes(), gate(gates, t.result())))
                .toList();
        return new RuleOutcome(gate(gates, computed.result()), teams, computed.evidence());
    }

    /**
     * C1's v0.1.9 compatibility for a result without a denominator: while the gates have not passed
     * it publishes {@code BLOCKED} (with its counts and reasons), never {@code NO_DENOMINATOR}. Only
     * the executor's legacy path asks for it; the split of limitations (S2) is where it is revisited.
     */
    public static RuleOutcome blockEmptyDenominators(RuleOutcome gated) {
        List<TeamResult> teams = gated.teams().stream()
                .map(t -> new TeamResult(t.ine(), t.cnes(), blockIfEmpty(t.result())))
                .toList();
        return new RuleOutcome(blockIfEmpty(gated.result()), teams, gated.evidence());
    }

    private static IndicatorResult blockIfEmpty(IndicatorResult result) {
        if (result.status() != IndicatorStatus.NO_DENOMINATOR) {
            return result;
        }
        return new IndicatorResult(
                IndicatorStatus.BLOCKED,
                result.valueText(),
                result.numerator(),
                result.denominator(),
                result.denominatorKind(),
                result.classification(),
                result.referencePeriod(),
                result.ruleVersion(),
                result.dataCutoff(),
                result.municipalityIbge(),
                result.limitations(),
                result.calculationPolicyVersion(),
                result.valueKind(),
                result.valueExact(),
                result.components(),
                result.consolidationEligible());
    }
}
