package esusdata.run.job;

import java.io.Serial;

/** Same idempotency key reused with a different request payload (§1.9.5). Maps to HTTP 409. */
public final class JobRequestConflictException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public JobRequestConflictException(String message) {
        super(message);
    }
}
