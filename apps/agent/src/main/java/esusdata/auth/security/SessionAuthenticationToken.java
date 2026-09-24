package esusdata.auth.security;

import esusdata.auth.model.AuthenticatedSession;
import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Carries the validated {@link AuthenticatedSession} as the principal, not just a username. */
public final class SessionAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    // Lives only in a request attribute under a STATELESS policy (SecurityConfig), so it is never
    // serialized; transient keeps the raw token out of any stream if that ever changes.
    private final transient AuthenticatedSession session;
    private final transient String rawToken;

    public SessionAuthenticationToken(AuthenticatedSession session, String rawToken) {
        super(List.of(new SimpleGrantedAuthority("AUTHENTICATED")));
        this.session = session;
        this.rawToken = rawToken;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return rawToken;
    }

    @Override
    public Object getPrincipal() {
        return session;
    }

    public AuthenticatedSession session() {
        return session;
    }
}
