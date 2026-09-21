package br.gov.observatorioaps.extractionstore.domain;

/**
 * One canonical Atendimento event (§1.7 entity table), the unit the indicator-engine actually
 * consumes. {@code careDate} is an ISO-8601 {@code LocalDate} string (§1.7.2: assistential dates
 * are {@code LocalDate}, never coerced to a UTC instant).
 */
public record CanonicalEncounter(
        SourceRef sourceRef,
        String municipalityIbge,
        String careDate,
        CanonicalModality modality,
        String cnes,
        String ine,
        String cbo
) {
}
