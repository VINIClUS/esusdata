package esusdata.result.dto;

import esusdata.auth.dto.ScopeResponse;
import java.util.List;

/**
 * The result contract of §1.10 L395, plus the provenance of §1.8 L284 and the independent status
 * dimensions of §1.10.1 L407. A domain record ({@code PublishedResult}) is never returned
 * directly — this is the API's own shape. {@code value}/{@code numerator}/{@code denominator}
 * travel as canonical strings (§1.7.1 L245, ENG-26); {@code value == null} is distinct from
 * {@code value == "0"}.
 */
public record ResultResponse(
        String resultId,
        String jobId,
        String runId,
        String extractionId,
        String adapterVersion,
        String calculationPolicyVersion,
        String inputFingerprint,
        String indicatorPack,
        String ruleVersion,
        ScopeResponse scope,
        String referencePeriod,
        String status,
        String value,
        String unit,
        String numerator,
        String denominator,
        String denominatorKind,
        String classification,
        String dataCutoff,
        List<String> limitations,
        List<String> sourceRefs,
        String resultNature,
        String validationStatus,
        String completenessStatus,
        String consistencyLevel,
        String reproducibilityLevel,
        String canonicalSchemaVersion,
        String evidenceGrain,
        String appBuild,
        String publishedAt) {}
