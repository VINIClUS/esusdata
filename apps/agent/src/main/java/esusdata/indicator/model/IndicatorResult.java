package esusdata.indicator.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Required result fields per Tech Spec §1.10/§1.11: status, value, numerator, denominator,
 * denominator_kind, reference_period, rule_version, data_cutoff, scope, limitations. {@code
 * valueText} is {@code null} when {@code status == NO_DENOMINATOR} — distinct from the text
 * {@code "0"} (§1.10: "value: null é diferente de value: '0'").
 *
 * <p>ADR 0030 adds what composite indicators need (§1.10: "indicadores compostos expõem práticas
 * e subdenominadores"): the {@link ValueKind}, the exact value as a fraction ({@code valueExact},
 * never rounded), one {@link ResultComponent} per practice or subgroup, and whether the month counts
 * toward the quadrimestral mean of NT 8/2026 ({@code consolidationEligible} — C2 and C3 skip months
 * with no cohort event). For C7 {@code numerator} and {@code denominator} are {@code null}: its
 * value has no single pair (§2.4, item 23), only the components do.
 */
public record IndicatorResult(
        IndicatorStatus status,
        String valueText,
        BigInteger numerator,
        BigInteger denominator,
        String denominatorKind,
        Classification classification,
        String referencePeriod,
        String ruleVersion,
        String dataCutoff,
        String municipalityIbge,
        List<String> limitations,
        String calculationPolicyVersion,
        ValueKind valueKind,
        ExactRatio valueExact,
        List<ResultComponent> components,
        boolean consolidationEligible) {
    public IndicatorResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(valueKind, "valueKind");
        limitations = List.copyOf(limitations);
        components = List.copyOf(components);
    }

    /** C1's original shape: a percentage without components, eligible for consolidation. */
    public IndicatorResult(
            IndicatorStatus status,
            String valueText,
            BigInteger numerator,
            BigInteger denominator,
            String denominatorKind,
            Classification classification,
            String referencePeriod,
            String ruleVersion,
            String dataCutoff,
            String municipalityIbge,
            List<String> limitations,
            String calculationPolicyVersion) {
        this(
                status,
                valueText,
                numerator,
                denominator,
                denominatorKind,
                classification,
                referencePeriod,
                ruleVersion,
                dataCutoff,
                municipalityIbge,
                limitations,
                calculationPolicyVersion,
                ValueKind.PERCENTAGE,
                null,
                List.of(),
                true);
    }

    /** The same result with one more limitation (a disclosure of this run), once. */
    public IndicatorResult withLimitation(String limitation) {
        if (limitations.contains(limitation)) {
            return this;
        }
        List<String> more = new ArrayList<>(limitations);
        more.add(limitation);
        return new IndicatorResult(
                status,
                valueText,
                numerator,
                denominator,
                denominatorKind,
                classification,
                referencePeriod,
                ruleVersion,
                dataCutoff,
                municipalityIbge,
                more,
                calculationPolicyVersion,
                valueKind,
                valueExact,
                components,
                consolidationEligible);
    }

    public enum IndicatorStatus {
        COMPUTED,
        NO_DENOMINATOR,
        BLOCKED,
        /** The ficha leaves a case undefined (AMB-xx); the value stays unavailable (§4.2). */
        RULE_AMBIGUITY,
        /** The source lacks a capability the pack requires (§1.6) — never shown as zero. */
        UNSUPPORTED_SOURCE
    }
}
