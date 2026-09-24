package esusdata.run.job;

import java.io.Serial;

/** Thrown by a cooperative check when a cancellation has been requested (§1.9.4). */
public final class JobCancelledException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public JobCancelledException(String message) {
        super(message);
    }
}
