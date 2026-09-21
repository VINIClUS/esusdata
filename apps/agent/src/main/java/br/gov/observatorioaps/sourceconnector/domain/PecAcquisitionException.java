package br.gov.observatorioaps.sourceconnector.domain;

/**
 * Unchecked carrier for a checked failure (JDBC {@code SQLException}, local file {@code
 * IOException}) raised while acquiring from the PEC — so {@link AcquisitionPort#acquire} never
 * has to declare a checked exception. The cause chain is always preserved: job-runner's {@code
 * FailureClassifier} walks {@link #getCause()} to find a {@code SQLException} and classify by
 * SQLSTATE exactly as it did when the caller threw the checked exception directly.
 */
public final class PecAcquisitionException extends RuntimeException {
    public PecAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
