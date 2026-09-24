package esusdata.source.model;

import java.io.Serial;

/**
 * A requested source id does not exist. {@code api} maps this to the same 404
 * {@code ApiExceptionHandler} gives every other nonexistent/out-of-scope object (§1.10.1 L403).
 */
public final class SourceNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public SourceNotFoundException(String message) {
        super(message);
    }
}
