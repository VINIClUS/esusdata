package esusdata.result.model;

/**
 * A published {@code results} row — the seven independent status dimensions (§1.10.1) plus the
 * full numeric/provenance contract (§1.7/§1.8), all as canonical strings. Never a {@code double}.
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
        String publishedAt) {}
