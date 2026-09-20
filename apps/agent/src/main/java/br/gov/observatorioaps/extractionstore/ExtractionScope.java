package br.gov.observatorioaps.extractionstore;

import java.time.LocalDate;

/** Immutable source and period authorized for one extraction attempt. */
public record ExtractionScope(
        String sourceId,
        String municipalityIbge,
        String periodStart,
        String periodEndExclusive
) {
    public ExtractionScope {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("acquisition scope sourceId is required");
        }
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("acquisition scope municipality must be a 7-digit IBGE code");
        }
        LocalDate start = parseDate(periodStart, "period start");
        LocalDate end = parseDate(periodEndExclusive, "period end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("acquisition scope period end must be after period start");
        }
    }

    boolean contains(String sourceId, String municipalityIbge, LocalDate careDate) {
        return this.sourceId.equals(sourceId)
                && this.municipalityIbge.equals(municipalityIbge)
                && !careDate.isBefore(LocalDate.parse(periodStart))
                && careDate.isBefore(LocalDate.parse(periodEndExclusive));
    }

    void requireMatches(String sourceId, String municipalityIbge,
                        String periodStart, String periodEndExclusive) {
        if (!this.sourceId.equals(sourceId)
                || !this.municipalityIbge.equals(municipalityIbge)
                || !this.periodStart.equals(periodStart)
                || !this.periodEndExclusive.equals(periodEndExclusive)) {
            throw new IllegalArgumentException(
                    "manifest does not match the bound acquisition scope");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("acquisition scope " + field + " must be an ISO date", e);
        }
    }
}
