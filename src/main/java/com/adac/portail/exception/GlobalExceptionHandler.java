package com.adac.portail.exception;

import com.adac.portail.dto.response.ErrorResponse;
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

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCategoryAlreadyExists(CategoryAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * Method-security ({@code @PreAuthorize}, TICKET-047) denial — {@code CategoryController}'s
     * write endpoints are the first in this branch to use it. A {@code @PreAuthorize} check runs
     * as an AOP proxy around the controller method, inside {@code DispatcherServlet}, so this
     * {@code @RestControllerAdvice} resolves it before {@code ExceptionTranslationFilter} (in the
     * security filter chain, upstream of the dispatcher) would ever see it — in production that
     * means {@code SecurityConfig}'s {@code accessDeniedHandler} never actually fires for a
     * method-security denial, only for a URL-rule one, even though both produce the identical body
     * here. Also what makes a {@code @WebMvcTest} slice with the filter chain disabled (see
     * {@code CategoryControllerTest}) see a proper 403 at all, since there's no
     * {@code ExceptionTranslationFilter} running in that slice to fall back on. Spring Security 6's
     * {@code AuthorizationDeniedException} (what {@code @PreAuthorize} actually throws) extends
     * this class, so it's caught here too without a separate handler.
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
