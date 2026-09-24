package esusdata.web;

import java.io.Serial;

/**
 * A requested object genuinely does not exist. Mapped by {@link ApiExceptionHandler} to the
 * identical 404 an out-of-scope object gets (§1.10.1 L403) — callers must never special-case this
 * against {@link ScopeDeniedException}.
 */
public final class ApiNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ApiNotFoundException(String message) {
        super(message);
    }
}
