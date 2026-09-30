package esusdata.run.job;

import java.io.Serial;

/**
 * Another live job for the same fonte, município, indicador and competência is still active
 * (ADR 0026). Carries that job's id so the caller can follow it instead of starting a second
 * acquisition. Maps to HTTP 409 {@code ACTIVE_JOB_EXISTS}.
 */
public final class ActiveJobExistsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String activeJobId;

    public ActiveJobExistsException(String activeJobId) {
        this(activeJobId, null);
    }

    /** {@code cause} is the refused insert, when there was one. */
    public ActiveJobExistsException(String activeJobId, Throwable cause) {
        super("an active job already covers this competência: " + activeJobId, cause);
        this.activeJobId = activeJobId;
    }

    public String activeJobId() {
        return activeJobId;
    }
}
