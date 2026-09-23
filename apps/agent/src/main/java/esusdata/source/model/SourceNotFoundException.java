package esusdata.source.model;

/**
 * A requested source id does not exist. {@code api} maps this to the same 404
 * {@code ApiExceptionHandler} gives every other nonexistent/out-of-scope object (§1.10.1 L403).
 */
public final class SourceNotFoundException extends RuntimeException {
    public SourceNotFoundException(String message) {
        super(message);
    }
}
