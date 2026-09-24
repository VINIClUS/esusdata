package esusdata.run.job;

import java.io.Serial;
import java.time.Instant;

/** A source is on cooldown after an abandoned live acquisition (ENG-51). */
public final class SourceAcquisitionBlockedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Instant blockedUntil;

    public SourceAcquisitionBlockedException(String message, Instant blockedUntil) {
        super(message);
        this.blockedUntil = blockedUntil;
    }

    /** When the cooldown this exception was thrown for actually lifts — never before this instant. */
    public Instant blockedUntil() {
        return blockedUntil;
    }
}
