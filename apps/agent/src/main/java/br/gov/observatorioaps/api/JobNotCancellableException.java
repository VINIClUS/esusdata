package br.gov.observatorioaps.api;

/** The job's current state lost the cancel CAS — already terminal, or a concurrent worker transition won. Maps to 409. */
final class JobNotCancellableException extends RuntimeException {
    JobNotCancellableException(String message) {
        super(message);
    }
}
