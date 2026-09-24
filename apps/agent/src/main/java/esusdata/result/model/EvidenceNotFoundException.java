package esusdata.result.model;

import java.io.Serial;

/** Thrown when a result id does not resolve within the requested municipality scope. */
public final class EvidenceNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public EvidenceNotFoundException(String message) {
        super(message);
    }
}
