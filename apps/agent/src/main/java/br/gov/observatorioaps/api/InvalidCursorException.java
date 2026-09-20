package br.gov.observatorioaps.api;

/** Thrown by {@link EvidenceCursor#decode} on any malformed, tampered, or mismatched cursor. */
final class InvalidCursorException extends RuntimeException {
    InvalidCursorException(String message) {
        super(message);
    }
}
