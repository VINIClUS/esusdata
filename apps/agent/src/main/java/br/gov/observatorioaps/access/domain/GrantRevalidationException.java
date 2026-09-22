package br.gov.observatorioaps.access.domain;

/**
 * A principal that started (or is about to start) a job no longer holds the grant that
 * authorized it — checked against CURRENT grants, not the session or the original request
 * (§1.9.4 L365). Thrown before acquisition; the sibling refusal at publication time is
 * {@link br.gov.observatorioaps.results.domain.PublicationAuthorizationRefusedException}, kept
 * distinct so {@code JobWorker} routes it through the normal failure path instead of the
 * cancellation-race path.
 */
public final class GrantRevalidationException extends RuntimeException {
    public GrantRevalidationException(String message) {
        super(message);
    }
}
