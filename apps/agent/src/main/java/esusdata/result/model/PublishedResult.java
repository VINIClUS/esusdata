package esusdata.result.model;

import esusdata.indicator.model.ExactRatio;
import java.math.BigInteger;

/**
 * A published {@code results} row — the seven independent status dimensions (§1.10.1) plus the
 * full numeric/provenance contract (§1.7/§1.8), all as canonical strings. Never a {@code double}.
 *
 * <p>ADR 0030 (V10): what the value means ({@code valueKind}), the exact value as a fraction
 * ({@code valueExactNumerator}/{@code valueExactDenominator}, null unless the value exists), the
 * practices or subgroups and the per-team results as JSON ({@code componentsJson}, {@code
 * teamResultsJson}, read with {@code ResultJson}), and whether the month enters the quadrimestral
 * mean. {@code numeratorText}/{@code denominatorText} are null for a value without a single pair
 * (C7) or a pack that has not counted yet.
 */
public record PublishedResult(
        String resultId,
        String jobId,
        String runId,
        String sourceId,
        String indicatorPack,
        String ruleVersion,
        String municipalityIbge,
        String referencePeriod,
        String status,
        String valueText,
        String numeratorText,
        String denominatorText,
        String denominatorKind,
        String classification,
        String dataCutoff,
        String extractionId,
        String adapterVersion,
        String calculationPolicyVersion,
        String limitationsJson,
        String inputFingerprint,
        String resultNature,
        String validationStatus,
        String completenessStatus,
        String consistencyLevel,
        String reproducibilityLevel,
        String canonicalSchemaVersion,
        String evidenceGrain,
        String appBuild,
        String publishedAt,
        String valueKind,
        String valueExactNumerator,
        String valueExactDenominator,
        String componentsJson,
        String teamResultsJson,
        boolean consolidationEligible) {

    /** The exact value as stored (a reduced fraction), or {@code null} when the result has none. */
    public ExactRatio valueExact() {
        return valueExactNumerator == null || valueExactDenominator == null
                ? null
                : new ExactRatio(new BigInteger(valueExactNumerator), new BigInteger(valueExactDenominator));
    }
}
