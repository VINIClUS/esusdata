package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;

/**
 * Populates the security context from the opaque {@value SessionCookie#NAME} cookie —
 * deliberately not {@code HttpSession}-based (see {@link SessionService}'s own javadoc). A
 * request whose path is in {@code nonInteractivePaths} (SSE, heartbeat, polling {@code GET
 * /runs/{id}}) is validated with {@code interactive=false} so it never advances {@code
 * last_interactive_at} (ENG-44).
 *
 * <p>An absent or invalid cookie leaves the request anonymous — {@code SecurityConfig}'s
 * authorization rules are what actually reject it (401), not this filter.
 *
 * <p>The authenticated context is also saved through {@code securityContextRepository} (the same
 * {@link RequestAttributeSecurityContextRepository} instance {@code SecurityConfig} registers for
 * {@code SessionManagementFilter}). Without that, {@code SessionManagementFilter} sees {@code
 * containsContext(request) == false} on every request under {@code STATELESS} policy — since there
 * is no session to persist a "not new" marker in — and treats every authenticated request as a
 * fresh login, running {@code CsrfAuthenticationStrategy} and deleting the CSRF cookie it just
 * issued.
 */
public final class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final Clock clock;
    private final Set<String> nonInteractivePaths;
    private final RequestAttributeSecurityContextRepository securityContextRepository;

    public SessionAuthenticationFilter(
            SessionService sessionService, Clock clock, Set<String> nonInteractivePaths,
            RequestAttributeSecurityContextRepository securityContextRepository) {
        this.sessionService = sessionService;
        this.clock = clock;
        this.nonInteractivePaths = nonInteractivePaths;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawToken = readCookie(request);
        if (rawToken != null) {
            boolean interactive = !nonInteractivePaths.contains(request.getServletPath());
            Optional<AuthenticatedSession> session =
                    sessionService.validate(rawToken, clock.instant(), interactive);
            session.ifPresent(s -> {
                SecurityContext context = SecurityContextHolder.getContext();
                context.setAuthentication(new SessionAuthenticationToken(s, rawToken));
                securityContextRepository.saveContext(context, request, response);
            });
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionCookie.NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
