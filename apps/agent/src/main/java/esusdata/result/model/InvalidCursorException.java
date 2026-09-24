package esusdata.result.model;

import esusdata.result.dto.EvidenceCursor;
import java.io.Serial;

/** Thrown by {@link EvidenceCursor#decode} on any malformed, tampered, or mismatched cursor. */
public final class InvalidCursorException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCursorException(String message) {
        super(message);
    }

    public InvalidCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
