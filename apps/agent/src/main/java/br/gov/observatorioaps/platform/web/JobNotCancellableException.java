package br.gov.observatorioaps.platform.web;

/** The job's current state lost the cancel CAS — already terminal, or a concurrent worker transition won. Maps to 409. */
public final class JobNotCancellableException extends RuntimeException {
    public JobNotCancellableException(String message) {
        super(message);
    }
}
