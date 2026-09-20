package br.gov.observatorioaps.resultstore;

import br.gov.observatorioaps.extractionstore.ExtractionManifest;

import java.time.Instant;

/**
 * Inputs to publish one sealed staging row as a result (§1.9.5). {@code resultNature} and {@code
 * validationStatus} are the two of the seven independent status dimensions (§1.10.1) that the
 * caller (the rule pack's run executor) must decide — the other five come from the manifest or
 * from this publication itself.
 */
public record PublicationRequest(
        String jobId,
        String runId,
        String stagingId,
        String sourceId,
        long executionGeneration,
        String processInstanceId,
        ExtractionManifest extractionManifest,
        String resultNature,
        String validationStatus,
        String appBuild,
        Instant publishedAt
) {
}
