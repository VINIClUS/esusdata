package br.gov.observatorioaps.execution.domain.job;

/**
 * A requested source id does not exist. {@code platform.web.ScopeCheckedAdvice} maps this to the
 * same 404 every other nonexistent/out-of-scope object gets (§1.10.1 L403).
 */
public final class SourceNotFoundException extends RuntimeException {
    public SourceNotFoundException(String message) {
        super(message);
    }
}
