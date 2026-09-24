package esusdata.result.model;

import java.io.Serial;

/**
 * Publication was refused because the job's ownership (process/generation/state) or the staging
 * row's state changed since the run started — the cancel/publish race resolved against
 * publication (§1.9.4: "não declarar CANCELLED enquanto ainda puder haver publicação" and its
 * mirror, never publish once the job left the state this run believed it owned).
 */
public final class PublicationRefusedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PublicationRefusedException(String message) {
        super(message);
    }
}
