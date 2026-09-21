package br.gov.observatorioaps.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import br.gov.observatorioaps.api.error.ApiError;

/**
 * ENG-49: rejects a request whose {@code Origin} (when present) or {@code Host} header is not on
 * the configured allowlist — loopback bind is not an exemption. A same-origin browser navigation
 * may omit {@code Origin}, so only {@code Host} is required on every request; {@code Origin} is
 * checked whenever the browser sends one (every cross-origin and most state-changing requests).
 */
public final class OriginHostValidationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;
    private final Set<String> allowedHosts;
    private final ObjectMapper mapper = new ObjectMapper();

    public OriginHostValidationFilter(Set<String> allowedOrigins, Set<String> allowedHosts) {
        this.allowedOrigins = allowedOrigins;
        this.allowedHosts = allowedHosts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String host = request.getHeader("Host");
        if (host == null || !allowedHosts.contains(host)) {
            reject(response, "Host header is not on the allowed list");
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            reject(response, "Origin header is not on the allowed list");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(mapper.writeValueAsString(new ApiError("ORIGIN_NOT_ALLOWED", message)));
    }
}
