package esusdata.run.controller;

public record AttemptResponse(
        int attempt, String startedAt, String finishedAt, String outcome, String failureCode,
        String failureDetail) {
}
