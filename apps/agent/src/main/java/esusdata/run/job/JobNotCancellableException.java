package esusdata.run.job;

import java.io.Serial;

/** The job's current state lost the cancel CAS — already terminal, or a concurrent worker transition won. Maps to 409. */
public final class JobNotCancellableException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public JobNotCancellableException(String message) {
        super(message);
    }
}
