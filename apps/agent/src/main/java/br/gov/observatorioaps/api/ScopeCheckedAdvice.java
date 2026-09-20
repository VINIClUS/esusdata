package br.gov.observatorioaps.api;

import br.gov.observatorioaps.resultstore.EvidenceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global HTTP error mapping for the domain controllers (results, evidence, indicator packs, and
 * onward). {@code AuthController}'s own {@code @ExceptionHandler}s stay local to that controller
 * (Spring gives controller-local handlers precedence) — this advice never touches auth's error
 * shapes, so {@code AuthErrorResponsesTest} keeps passing unmodified.
 */
@RestControllerAdvice
class ScopeCheckedAdvice {

    /**
     * §1.10.1 L403: "objeto inexistente e objeto fora do escopo têm a mesma resposta externa
     * 404." This handler MUST NOT vary status, body, or message by which of the three exceptions
     * it caught — the diagnostic difference lives only in {@code auth_audit}, not in the response.
     */
    @ExceptionHandler({ScopeDeniedException.class, ApiNotFoundException.class, EvidenceNotFoundException.class})
    ResponseEntity<ApiError> handleNotFoundOrOutOfScope() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "not found"));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ApiError> handleInvalidCursor() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_CURSOR", "the evidence cursor is malformed or expired"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", e.getMessage()));
    }
}
