package br.gov.observatorioaps.execution.adapter.in.http;

import java.util.List;

/**
 * {@code failureCode} is one of the distinguishable classes {@code FailureClassifier} names
 * (§1.10 L399) — never collapsed to a generic error, and never a fabricated 0% result.
 */
public record RunResponse(
        String jobId, String runId, String state, int attempt, int maxAttempts,
        String municipalityIbge, String indicatorPack, String ruleVersion, String referencePeriod,
        String sourceId, String extractionId, String createdAt, String startedAt, String finishedAt,
        String lastProgressAt, String failureCode, String failureDetail, String resultId,
        List<AttemptResponse> attempts) {
}
