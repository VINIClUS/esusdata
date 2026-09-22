package esusdata.web;

/**
 * A requested object genuinely does not exist. Mapped by {@link ApiExceptionHandler} to the
 * identical 404 an out-of-scope object gets (§1.10.1 L403) — callers must never special-case this
 * against {@link ScopeDeniedException}.
 */
public final class ApiNotFoundException extends RuntimeException {
    public ApiNotFoundException(String message) {
        super(message);
    }
}
