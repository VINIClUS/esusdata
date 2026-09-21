package br.gov.observatorioaps.resultstore.domain;

/** The published result id and the reproducibility level decided at publication time. */
public record PublicationOutcome(String resultId, String reproducibilityLevel) {
}
