package br.gov.observatorioaps.resultstore.domain;

/**
 * The one seam through which {@code PublicationService} asks whether a job's original
 * authorization is still current, without depending on {@code identityaccess} — result-store must
 * stay readable/testable with the PEC disconnected and, by the same reasoning, without the
 * identity subsystem wired in (an invariant {@code ModuleBoundaryTest} enforces). Implemented by
 * {@code identityaccess.GrantRevalidator}.
 *
 * <p>§1.9.4 L365: "Revalidar as concessões do usuário/escopo antes da aquisição e da publicação...
 * Revogação de acesso ou bloqueio da conta impede novo acesso/publicação sob aquele pedido, sem
 * apagar histórico anterior."
 */
public interface PublicationAuthorization {

    /**
     * @throws PublicationAuthorizationRefusedException if {@code principal} no longer holds a
     *         current grant sufficient to publish for {@code municipalityIbge}.
     */
    void requireStillAuthorized(String principal, String municipalityIbge);

    /** A permissive default for call sites (mostly tests) that do not exercise authorization. */
    static PublicationAuthorization allowAll() {
        return (principal, municipalityIbge) -> { };
    }
}
