package esusdata.run.controller;

import java.io.Serial;

/** {@link SseConnectionLimiter} refused a new {@code GET /runs/{id}/events} connection. */
public final class TooManyEventStreamsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyEventStreamsException(String message) {
        super(message);
    }
}
