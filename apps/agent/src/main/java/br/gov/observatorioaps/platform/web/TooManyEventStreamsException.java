package br.gov.observatorioaps.platform.web;

import br.gov.observatorioaps.execution.adapter.in.http.SseConnectionLimiter;

/** {@link SseConnectionLimiter} refused a new {@code GET /runs/{id}/events} connection. */
public final class TooManyEventStreamsException extends RuntimeException {
    public TooManyEventStreamsException(String message) {
        super(message);
    }
}
