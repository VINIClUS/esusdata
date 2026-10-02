package esusdata.indicator.model;

import java.util.List;

/**
 * A recorded end of pregnancy (§1.7 "Gestação"; kind {@code pregnancy_outcome}), when the source
 * has one. Episodes are built by the C3 rule, not by the query; without a recorded outcome the
 * ficha's substitute date applies and the rule shows which one it used (§2.4).
 */
public record CanonicalPregnancyOutcome(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String outcomeDate,
        String outcomeType,
        List<String> codes) {
    public CanonicalPregnancyOutcome {
        codes = codes == null ? List.of() : List.copyOf(codes);
    }
}
