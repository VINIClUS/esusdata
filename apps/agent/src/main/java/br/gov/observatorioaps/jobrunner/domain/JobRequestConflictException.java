package br.gov.observatorioaps.jobrunner.domain;

/** Same idempotency key reused with a different request payload (§1.9.5). Maps to HTTP 409. */
public final class JobRequestConflictException extends RuntimeException {
    public JobRequestConflictException(String message) {
        super(message);
    }
}
