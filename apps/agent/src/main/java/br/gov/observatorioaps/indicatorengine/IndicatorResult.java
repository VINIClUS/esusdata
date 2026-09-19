package br.gov.observatorioaps.indicatorengine;

import java.math.BigInteger;
import java.util.List;

/**
 * Required result fields per Tech Spec §1.10/§1.11: status, value, numerator, denominator,
 * denominator_kind, reference_period, rule_version, data_cutoff, scope, limitations. {@code
 * valueText} is {@code null} when {@code status == NO_DENOMINATOR} — distinct from the text
 * {@code "0"} (§1.10: "value: null é diferente de value: '0'").
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
        String calculationPolicyVersion
) {
    public enum IndicatorStatus {
        COMPUTED,
        NO_DENOMINATOR
    }
}
