package esusdata.auth.model;

import java.io.Serial;

/** Deliberately the same exception/message for "no such user" and "wrong password" (§1.12.7). */
public final class AuthenticationFailedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
