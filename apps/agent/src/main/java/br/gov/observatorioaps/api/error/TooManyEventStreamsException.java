package br.gov.observatorioaps.api.error;

import br.gov.observatorioaps.api.runs.SseConnectionLimiter;

/** {@link SseConnectionLimiter} refused a new {@code GET /runs/{id}/events} connection. */
public final class TooManyEventStreamsException extends RuntimeException {
    public TooManyEventStreamsException(String message) {
        super(message);
    }
}
