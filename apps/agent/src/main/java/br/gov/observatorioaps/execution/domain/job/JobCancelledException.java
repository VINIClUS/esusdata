package br.gov.observatorioaps.execution.domain.job;

/** Thrown by a cooperative check when a cancellation has been requested (§1.9.4). */
public final class JobCancelledException extends RuntimeException {
    public JobCancelledException(String message) {
        super(message);
    }
}
