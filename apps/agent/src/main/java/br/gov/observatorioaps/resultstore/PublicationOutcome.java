package br.gov.observatorioaps.resultstore;

/** The published result id and the reproducibility level decided at publication time. */
public record PublicationOutcome(String resultId, String reproducibilityLevel) {
}
