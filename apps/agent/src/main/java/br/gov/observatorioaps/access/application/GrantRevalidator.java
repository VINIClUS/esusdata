package br.gov.observatorioaps.access.application;

import br.gov.observatorioaps.results.domain.PublicationAuthorization;
import br.gov.observatorioaps.results.domain.PublicationAuthorizationRefusedException;
import br.gov.observatorioaps.access.domain.GrantRevalidationException;
import br.gov.observatorioaps.access.domain.Permission;
import br.gov.observatorioaps.access.domain.UserAccount;
import br.gov.observatorioaps.access.domain.UserState;
import br.gov.observatorioaps.access.domain.UserRepository;
/**
 * §1.9.4 L365: "Revalidar as concessões do usuário/escopo antes da aquisição e da publicação,
 * independentemente da sessão do navegador... Revogação de acesso ou bloqueio da conta impede
 * novo acesso/publicação sob aquele pedido, sem apagar histórico anterior." Used directly by
 * {@code execution} before acquisition starts ({@link #requireCurrentlyAuthorized}), and by
 * {@code results} before publication commits, through the {@link PublicationAuthorization}
 * seam this class implements — {@code results} never depends on {@code access} directly.
 */
public final class GrantRevalidator implements PublicationAuthorization {

    private final ScopeResolver scopeResolver;
    private final UserRepository userRepository;

    public GrantRevalidator(ScopeResolver scopeResolver, UserRepository userRepository) {
        this.scopeResolver = scopeResolver;
        this.userRepository = userRepository;
    }

    /**
     * Checked before a job starts acquisition/computation, whether the acquisition mode is
     * {@code IMMUTABLE_EXTRACT} or {@code LIVE_READ_ONLY} — L365 does not distinguish between
     * them. Closing a browser tab, logging out, or a session expiring does NOT revoke a job
     * already authorized; only an actual grant/account change does.
     */
    public void requireCurrentlyAuthorized(String principal, String municipalityIbge, Permission permission) {
        if (principal == null) {
            throw new GrantRevalidationException("job has no recorded principal; cannot authorize");
        }
        UserAccount user = userRepository.findById(principal).orElse(null);
        if (user == null || user.state() != UserState.ACTIVE) {
            throw new GrantRevalidationException(
                    "principal " + principal + " is not an active user — access revoked or blocked");
        }
        if (!scopeResolver.hasPermission(principal, permission, municipalityIbge)) {
            throw new GrantRevalidationException(
                    "principal " + principal + " no longer holds " + permission.dbValue()
                            + " for municipality " + municipalityIbge);
        }
    }

    @Override
    public void requireStillAuthorized(String principal, String municipalityIbge) {
        try {
            requireCurrentlyAuthorized(principal, municipalityIbge, Permission.RUN_INDICATOR);
        } catch (GrantRevalidationException revoked) {
            throw new PublicationAuthorizationRefusedException(
                    "publication refused — authorization no longer current (§1.9.4 L365): "
                            + revoked.getMessage());
        }
    }
}
