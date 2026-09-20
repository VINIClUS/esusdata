package br.gov.observatorioaps.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** §1.12.6 L427: every clinical/API response is {@code Cache-Control: no-store}, never cached. */
public final class NoStoreCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        filterChain.doFilter(request, response);
    }
}
