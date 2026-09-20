package br.gov.observatorioaps.api;

/**
 * An authenticated principal lacks the permission/scope for a specific, existing object. Mapped
 * by {@link ScopeCheckedAdvice} to the SAME 404 response a genuinely nonexistent object gets —
 * §1.10.1 L403: "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404."
 * Callers must never special-case this against {@link ApiNotFoundException}.
 */
final class ScopeDeniedException extends RuntimeException {
    ScopeDeniedException(String message) {
        super(message);
    }
}
