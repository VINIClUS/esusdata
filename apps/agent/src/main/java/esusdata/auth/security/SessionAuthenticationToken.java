package esusdata.auth.security;

import esusdata.auth.model.AuthenticatedSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Carries the validated {@link AuthenticatedSession} as the principal, not just a username. */
public final class SessionAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedSession session;
    private final String rawToken;

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
