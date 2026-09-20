package br.gov.observatorioaps.resultstore;

/**
 * Publication was refused because the job's principal no longer holds a current grant for the
 * job's municipality (§1.9.4 L365) — distinct from {@link PublicationRefusedException} (an
 * ownership/state race) so {@code JobWorker} routes this one through the normal failure path
 * (job -> FAILED, diagnosable code) instead of the cancellation-race path, which only acts when
 * the job is actually {@code CANCEL_REQUESTED}.
 */
public final class PublicationAuthorizationRefusedException extends RuntimeException {
    public PublicationAuthorizationRefusedException(String message) {
        super(message);
    }
}
