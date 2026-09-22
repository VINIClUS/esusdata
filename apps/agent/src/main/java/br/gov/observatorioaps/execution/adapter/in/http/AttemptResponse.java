package br.gov.observatorioaps.execution.adapter.in.http;

public record AttemptResponse(
        int attempt, String startedAt, String finishedAt, String outcome, String failureCode,
        String failureDetail) {
}
