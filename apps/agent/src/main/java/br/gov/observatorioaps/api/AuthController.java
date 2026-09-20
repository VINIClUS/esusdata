package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticationFailedException;
import br.gov.observatorioaps.identityaccess.AuthenticationService;
import br.gov.observatorioaps.identityaccess.BootstrapActivation;
import br.gov.observatorioaps.identityaccess.LoginThrottle;
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
            @AuthenticationPrincipal br.gov.observatorioaps.identityaccess.AuthenticatedSession session,
            HttpServletRequest httpRequest) {
        authenticationService.logout(session.sessionId(), session.userId(), clock.instant());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(httpRequest).toString())
                .build();
    }

    @GetMapping("/api/v1/auth/me")
    public MeResponse me(
            @AuthenticationPrincipal br.gov.observatorioaps.identityaccess.AuthenticatedSession session) {
        return new MeResponse(session.userId());
    }

    @PostMapping("/api/v1/auth/activate")
    public ResponseEntity<Void> activate(@RequestBody ActivateRequest request) {
        bootstrapActivation.activate(request.token(), request.password(), clock.instant());
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
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", e.retryAfter().toString())
                .body(new ApiError("LOGIN_THROTTLED", "too many failed attempts; try again later"));
    }

    @ExceptionHandler(BootstrapActivation.ActivationFailedException.class)
    public ResponseEntity<ApiError> handleActivationFailed(BootstrapActivation.ActivationFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("ACTIVATION_FAILED", e.getMessage()));
    }
}
