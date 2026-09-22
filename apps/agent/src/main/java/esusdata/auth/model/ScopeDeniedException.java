package esusdata.auth.model;

import esusdata.web.ApiExceptionHandler;
/**
 * An authenticated principal lacks the permission/scope for a specific, existing object. Mapped
 * by {@link ApiExceptionHandler} to the SAME 404 response a genuinely nonexistent object gets —
 * §1.10.1 L403: "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404."
 * Callers must never special-case this against {@link ApiNotFoundException}.
 */
public final class ScopeDeniedException extends RuntimeException {
    public ScopeDeniedException(String message) {
        super(message);
    }
}
