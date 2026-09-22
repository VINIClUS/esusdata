package br.gov.observatorioaps.platform.web;

/** {@code SseConnectionLimiter} refused a new {@code GET /runs/{id}/events} connection. */
public final class TooManyEventStreamsException extends RuntimeException {
    public TooManyEventStreamsException(String message) {
        super(message);
    }
}
