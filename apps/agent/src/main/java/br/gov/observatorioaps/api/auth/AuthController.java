package br.gov.observatorioaps.api.auth;

import br.gov.observatorioaps.identityaccess.domain.AuthenticationFailedException;
import br.gov.observatorioaps.identityaccess.application.AuthenticationService;
import br.gov.observatorioaps.identityaccess.application.BootstrapActivation;
import br.gov.observatorioaps.identityaccess.application.LoginThrottle;
import br.gov.observatorioaps.identityaccess.application.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import br.gov.observatorioaps.api.error.ApiError;
import br.gov.observatorioaps.api.security.NoStoreCacheControlFilter;
import br.gov.observatorioaps.api.security.SessionCookie;

/**
 * The auth HTTP surface the spec leaves undefined (plan decision 5) —
 * {@code docs/adr/0008-superficie-http-de-autenticacao.md}. Every response here also carries
 * {@code Cache-Control: no-store}, applied by {@code NoStoreCacheControlFilter} to the whole
 * {@code /api/**} matcher, not repeated per method.
 */
@RestController
public class AuthController {

    private final AuthenticationService authenticationService;
    private final BootstrapActivation bootstrapActivation;
    private final Clock clock;

    public AuthController(
            AuthenticationService authenticationService, BootstrapActivation bootstrapActivation, Clock clock) {
        this.authenticationService = authenticationService;
        this.bootstrapActivation = bootstrapActivation;
        this.clock = clock;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var result = authenticationService.login(
                request.username(), request.password(), httpRequest.getRemoteAddr(), clock.instant());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newSessionCookie(result.rawToken(), httpRequest).toString())
                .body(new LoginResponse(result.userId(), result.displayName()));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal br.gov.observatorioaps.identityaccess.domain.AuthenticatedSession session,
            HttpServletRequest httpRequest) {
        authenticationService.logout(session.sessionId(), session.userId(), clock.instant());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(httpRequest).toString())
                .build();
    }

    @GetMapping("/api/v1/auth/me")
    public MeResponse me(
            @AuthenticationPrincipal br.gov.observatorioaps.identityaccess.domain.AuthenticatedSession session) {
        return new MeResponse(session.userId());
    }

    @PostMapping("/api/v1/auth/activate")
    public ResponseEntity<Void> activate(@RequestBody ActivateRequest request) {
        bootstrapActivation.activate(request.token(), request.password(), clock.instant());
        return ResponseEntity.noContent().build();
    }

    /** §1.12.7 L539 explicit re-verification, ahead of a grant/revoke/source-secret action. */
    @PostMapping("/api/v1/auth/reauth")
    public ResponseEntity<Void> reauth(
            @AuthenticationPrincipal br.gov.observatorioaps.identityaccess.domain.AuthenticatedSession session,
            @RequestBody ReauthRequest request, HttpServletRequest httpRequest) {
        authenticationService.reauthenticate(
                session.sessionId(), session.userId(), request.password(),
                httpRequest.getRemoteAddr(), clock.instant());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie newSessionCookie(String rawToken, HttpServletRequest httpRequest) {
        return ResponseCookie.from(SessionCookie.NAME, rawToken)
                .httpOnly(true)
                .secure(httpRequest.isSecure())
                .sameSite("Strict")
                .path("/")
                .build();
    }

    private ResponseCookie clearedSessionCookie(HttpServletRequest httpRequest) {
        return ResponseCookie.from(SessionCookie.NAME, "")
                .httpOnly(true)
                .secure(httpRequest.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("AUTHENTICATION_FAILED", "invalid username or password"));
    }

    @ExceptionHandler(LoginThrottle.LoginThrottledException.class)
    public ResponseEntity<ApiError> handleThrottled(LoginThrottle.LoginThrottledException e) {
        // Retry-After (RFC 7231 §7.1.3) accepts delta-seconds or an HTTP-date — never a plain
        // ISO-8601 instant, which Instant#toString() produces and clients/intermediaries may
        // reject or ignore outright. Ceiling (not floor) the remaining delay: truncating a
        // sub-second remainder to 0 would tell a still-throttled client to retry immediately.
        Duration remaining = Duration.between(clock.instant(), e.retryAfter());
        long retryAfterSeconds = remaining.isPositive()
                ? (remaining.toMillis() + 999) / 1000
                : 0;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(new ApiError("LOGIN_THROTTLED", "too many failed attempts; try again later"));
    }

    @ExceptionHandler(BootstrapActivation.ActivationFailedException.class)
    public ResponseEntity<ApiError> handleActivationFailed(BootstrapActivation.ActivationFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("ACTIVATION_FAILED", e.getMessage()));
    }

    @ExceptionHandler(PasswordPolicy.WeakPasswordException.class)
    public ResponseEntity<ApiError> handleWeakPassword(PasswordPolicy.WeakPasswordException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("WEAK_PASSWORD", e.getMessage()));
    }
}
