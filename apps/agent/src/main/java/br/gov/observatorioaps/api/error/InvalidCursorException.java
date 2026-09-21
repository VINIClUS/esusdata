package br.gov.observatorioaps.api.error;

import br.gov.observatorioaps.api.results.EvidenceCursor;

/** Thrown by {@link EvidenceCursor#decode} on any malformed, tampered, or mismatched cursor. */
public final class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super(message);
    }
}
