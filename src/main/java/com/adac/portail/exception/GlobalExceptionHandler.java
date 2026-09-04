package com.adac.portail.exception;

import com.adac.portail.dto.response.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * First entries — grows as later tickets add more domain exceptions (see ARCHI.md). Predates
 * TICKET-014's login/me errors, which stay handled where they're raised (the security filters run
 * before any {@code @ControllerAdvice} could apply — see {@link ErrorResponse}'s Javadoc).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ActivationTokenExpiredException.class, ActivationTokenInvalidException.class})
    public ResponseEntity<ErrorResponse> handleInvalidActivationToken(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * Last-resort fallback for a UNIQUE constraint the service layer's own pre-check didn't catch
     * — e.g. two concurrent {@code POST /api/users/formateurs} for the same email both pass
     * {@code UserServiceImpl}'s {@code findByEmail} check before either commits (TICKET-019
     * review: {@link DuplicateEmailException}'s own pre-check is TOCTOU, not race-proof). Kept
     * generic (not tied to the email message specifically) since this handler is global and any
     * future unique constraint could trigger it the same way; the specific, friendlier message
     * from the pre-check is still what callers see in the overwhelmingly common case.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflit : cette ressource existe déjà"));
    }

    /**
     * Method-security ({@code @PreAuthorize}, TICKET-019) denial. A {@code @PreAuthorize} check
     * runs as an AOP proxy around the controller method invocation, inside
     * {@code DispatcherServlet} — so this {@code @RestControllerAdvice} resolves it *before*
     * {@code ExceptionTranslationFilter} (in the security filter chain, upstream of the
     * dispatcher) ever sees it. In production that means {@code SecurityConfig}'s
     * {@code accessDeniedHandler} never actually fires for a method-security denial — only for a
     * URL-rule denial — even though both currently produce the identical JSON body here. Any
     * future denial-side logging/metrics belongs in *this* handler, not in
     * {@code SecurityConfig}. Also what makes a {@code @WebMvcTest} slice with the filter chain
     * disabled (see {@code UserControllerTest}) see a proper 403 at all, since there's no
     * {@code ExceptionTranslationFilter} running in that slice to fall back on.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Droits insuffisants"));
    }

    /**
     * A {@code @Valid @RequestBody} failure — without this, Spring Boot's default {@code /error}
     * body ({@code {timestamp, status, error, path}}) leaks through instead of docs/tech.md's
     * {@code {status, message, details}} shape, the one error format the rest of the API commits
     * to (see TICKET-015 review).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Requête invalide", details));
    }

    /**
     * Syntactically-invalid JSON (not a validation failure — the body never parsed at all, so
     * {@code @Valid} never ran). Without this, it falls through to Spring's default handling,
     * which returns 400 with an empty body instead of docs/tech.md's {@code {status, message,
     * details}} shape (found via manual probing during the TICKET-045 branch-wide review — the
     * theorized cause, an unauthenticated {@code /error} dispatch, turned out to be wrong; the
     * real gap was simply no handler for this specific exception).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Requête invalide"));
    }
}
