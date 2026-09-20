package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.SessionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;
import java.util.Set;

/**
 * The one place the HTTP filter chain is assembled. No {@code permitAll} beyond login, activation
 * and readiness (plan decision 7/§1.12.6). Session state is entirely {@link SessionService}-based
 * (STATELESS servlet session policy) — Spring's own {@code HttpSession}-backed security context is
 * never used, for the reasons {@link SessionService}'s javadoc gives.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(WebSecurityProperties.class)
public class SecurityConfig {

    private static final Set<String> NON_INTERACTIVE_PATHS = Set.of();

    /**
     * Suppresses Boot's {@code UserDetailsServiceAutoConfiguration} fallback, which activates
     * whenever no {@code UserDetailsService}/{@code AuthenticationManager}/{@code
     * AuthenticationProvider} bean exists — independent of whether a custom {@code
     * SecurityFilterChain} is defined — and logs a generated password at every boot (directly
     * contrary to L110/ENG-45's "sem senha padrão distribuída"). This bean is never actually
     * consulted: {@link SessionAuthenticationFilter} populates the security context directly from
     * {@link SessionService}, never through a {@code DaoAuthenticationProvider}.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                    "not used — authentication goes through SessionAuthenticationFilter, not UserDetailsService");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, SessionService sessionService, Clock clock, WebSecurityProperties webProperties)
            throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // CsrfConfigurer unconditionally calls SessionManagementConfigurer.addSessionAuthentication
        // Strategy(new CsrfAuthenticationStrategy(...)) — confirmed by decompiling CsrfConfigurer
        // (spring-security-config 7.1.1); it APPENDS to the composite, so overriding
        // .sessionAuthenticationStrategy(...) here cannot remove it. That strategy rotates
        // (deletes+reissues) the CSRF cookie whenever SessionManagementFilter thinks it's seeing a
        // fresh login. Under STATELESS with the default NullSecurityContextRepository,
        // containsContext(request) is always false, so EVERY authenticated request looks like a
        // fresh login here (there's no HttpSession to carry a "already handled" marker) — the
        // rotation fired on every request and deleted the cookie the client had just been given.
        // The actual fix is upstream of sessionAuthenticationStrategy: give SessionManagementFilter
        // a repository that can report "already established" WITHIN this request.
        // SessionAuthenticationFilter saves the context through this same instance once it
        // re-derives auth from the session cookie, so by the time SessionManagementFilter runs,
        // containsContext(request) is true and the whole strategy chain (CSRF rotation included) is
        // skipped.
        RequestAttributeSecurityContextRepository securityContextRepository =
                new RequestAttributeSecurityContextRepository();

        http
                .securityMatcher("/api/**")
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'none'")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/ready", "/api/v1/auth/login", "/api/v1/auth/activate")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new SessionAuthenticationFilter(
                                sessionService, clock, NON_INTERACTIVE_PATHS, securityContextRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new OriginHostValidationFilter(
                                Set.copyOf(webProperties.allowedOrigins()), Set.copyOf(webProperties.allowedHosts())),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new NoStoreCacheControlFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
