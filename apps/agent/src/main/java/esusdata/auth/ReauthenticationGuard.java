package esusdata.auth;

import esusdata.auth.model.AuthenticatedSession;
import java.io.Serial;
import java.time.Instant;

/**
 * §1.12.7 L537: "Reautenticação realizada nos últimos cinco minutos para concessão de acesso,
 * mudança de destino/segredo ou exportação individualizada." One explicit check point for those
 * sensitive actions, rather than relying on callers to remember the five-minute rule themselves.
 */
public final class ReauthenticationGuard {

    private final SessionService sessionService;

    public ReauthenticationGuard(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void requireRecentReauth(AuthenticatedSession session, Instant now) {
        if (!sessionService.reauthenticatedRecently(session, now)) {
            throw new ReauthenticationRequiredException(
                    "this action requires reauthentication within the last few minutes");
        }
    }

    public static final class ReauthenticationRequiredException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ReauthenticationRequiredException(String message) {
            super(message);
        }
    }
}
