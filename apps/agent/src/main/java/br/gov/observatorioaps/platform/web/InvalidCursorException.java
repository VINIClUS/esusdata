package br.gov.observatorioaps.platform.web;

/** Thrown by {@code EvidenceCursor#decode} on any malformed, tampered, or mismatched cursor. */
public final class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super(message);
    }
}
