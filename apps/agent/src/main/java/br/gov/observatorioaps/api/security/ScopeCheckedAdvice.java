package br.gov.observatorioaps.api.security;

import br.gov.observatorioaps.identityaccess.application.AccessAdministrationService;
import br.gov.observatorioaps.identityaccess.application.ReauthenticationGuard;
import br.gov.observatorioaps.identityaccess.application.UserProvisioning;
import br.gov.observatorioaps.jobrunner.domain.JobRequestConflictException;
import br.gov.observatorioaps.jobrunner.domain.SourceNotFoundException;
import br.gov.observatorioaps.resultstore.domain.EvidenceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import br.gov.observatorioaps.api.auth.AuthController;
import br.gov.observatorioaps.api.error.ApiError;
import br.gov.observatorioaps.api.error.ApiNotFoundException;
import br.gov.observatorioaps.api.error.InvalidCursorException;
import br.gov.observatorioaps.api.error.JobNotCancellableException;
import br.gov.observatorioaps.api.error.ScopeDeniedException;
import br.gov.observatorioaps.api.error.TooManyEventStreamsException;
import br.gov.observatorioaps.api.runs.SseConnectionLimiter;

/**
 * Global HTTP error mapping for the domain controllers (results, evidence, indicator packs, and
 * onward). {@code AuthController}'s own {@code @ExceptionHandler}s stay local to that controller
 * (Spring gives controller-local handlers precedence) — this advice never touches auth's error
 * shapes, so {@code AuthErrorResponsesTest} keeps passing unmodified.
 */
@RestControllerAdvice
public class ScopeCheckedAdvice {

    /**
     * §1.10.1 L403: "objeto inexistente e objeto fora do escopo têm a mesma resposta externa
     * 404." This handler MUST NOT vary status, body, or message by which of the three exceptions
     * it caught — the diagnostic difference lives only in {@code auth_audit}, not in the response.
     */
    @ExceptionHandler({
            ScopeDeniedException.class, ApiNotFoundException.class, EvidenceNotFoundException.class,
            AccessAdministrationService.UserNotFoundException.class,
            AccessAdministrationService.GrantNotFoundException.class,
            SourceNotFoundException.class})
    ResponseEntity<ApiError> handleNotFoundOrOutOfScope() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "not found"));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ApiError> handleInvalidCursor() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_CURSOR", "the evidence cursor is malformed or expired"));
    }

    /** ENG-45: refused unconditionally by {@code AccessAdministrationService}, never silently ignored. */
    @ExceptionHandler(AccessAdministrationService.SelfGrantForbiddenException.class)
    ResponseEntity<ApiError> handleSelfGrantForbidden(AccessAdministrationService.SelfGrantForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("SELF_GRANT_FORBIDDEN", e.getMessage()));
    }

    /** A sole admin self-blocking would brick the installation — see {@code AccessAdministrationService.block}. */
    @ExceptionHandler(AccessAdministrationService.SelfBlockForbiddenException.class)
    ResponseEntity<ApiError> handleSelfBlockForbidden(AccessAdministrationService.SelfBlockForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("SELF_BLOCK_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(UserProvisioning.UsernameAlreadyExistsException.class)
    ResponseEntity<ApiError> handleUsernameAlreadyExists(UserProvisioning.UsernameAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("USERNAME_ALREADY_EXISTS", e.getMessage()));
    }

    /** §1.12.7 L539: the caller is authenticated but must step up with a fresh password check. */
    @ExceptionHandler(ReauthenticationGuard.ReauthenticationRequiredException.class)
    ResponseEntity<ApiError> handleReauthenticationRequired(ReauthenticationGuard.ReauthenticationRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("REAUTHENTICATION_REQUIRED", e.getMessage()));
    }

    /** ENG-24/§1.9.5: the same idempotency key was already used with a different request payload. */
    @ExceptionHandler(JobRequestConflictException.class)
    ResponseEntity<ApiError> handleJobRequestConflict(JobRequestConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
    }

    /** The job's current state lost the cancel CAS — already terminal, or the worker won the race. */
    @ExceptionHandler(JobNotCancellableException.class)
    ResponseEntity<ApiError> handleJobNotCancellable(JobNotCancellableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("JOB_NOT_CANCELLABLE", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", e.getMessage()));
    }

    /** {@link SseConnectionLimiter} refused a new stream — the fast-cadence load bound from the plan. */
    @ExceptionHandler(TooManyEventStreamsException.class)
    ResponseEntity<ApiError> handleTooManyEventStreams(TooManyEventStreamsException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("TOO_MANY_EVENT_STREAMS", e.getMessage()));
    }
}
