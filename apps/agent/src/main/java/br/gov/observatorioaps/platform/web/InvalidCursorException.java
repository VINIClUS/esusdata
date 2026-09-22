package br.gov.observatorioaps.platform.web;

import br.gov.observatorioaps.results.adapter.in.http.EvidenceCursor;

/** Thrown by {@link EvidenceCursor#decode} on any malformed, tampered, or mismatched cursor. */
public final class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super(message);
    }
}
