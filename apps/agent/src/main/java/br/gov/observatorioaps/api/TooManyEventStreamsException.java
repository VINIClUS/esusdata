package br.gov.observatorioaps.api;

/** {@link SseConnectionLimiter} refused a new {@code GET /runs/{id}/events} connection. */
final class TooManyEventStreamsException extends RuntimeException {
    TooManyEventStreamsException(String message) {
        super(message);
    }
}
