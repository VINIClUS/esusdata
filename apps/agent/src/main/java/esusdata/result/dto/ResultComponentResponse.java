package esusdata.result.dto;

/**
 * One practice (C2–C6) or subgroup (C7) of a result (ADR 0030): exact counts as canonical integer
 * strings, the value {@code numerator/denominator} on the 0–1 scale as a decimal string for display
 * and as an exact fraction, and the component's own status — {@code NO_DENOMINATOR} or {@code
 * RULE_AMBIGUITY} keep their counts and have no value, never a zero.
 */
public record ResultComponentResponse(
        String code,
        String kind,
        String weight,
        String numerator,
        String denominator,
        String value,
        ExactValue valueExact,
        String status) {}
