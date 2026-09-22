package br.gov.observatorioaps.execution.adapter.in.http;

/**
 * {@code sourceId} is always required — {@code results.source_id} is {@code NOT NULL} even for a
 * replayed extract. {@code extractionId}, when present, selects IMMUTABLE_EXTRACT replay over a
 * fresh LIVE_READ_ONLY acquisition; it never substitutes for {@code sourceId}.
 */
public record CreateRunRequest(
        String municipalityIbge, String indicatorPack, String ruleVersion, String referencePeriod,
        String sourceId, String extractionId) {
}
