package com.adac.portail.exception;

import com.adac.portail.dto.response.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/**
 * First entries — grows as later tickets add more domain exceptions (see ARCHI.md). Predates
 * TICKET-014's login/me errors, which stay handled where they're raised (the security filters run
 * before any {@code @ControllerAdvice} could apply — see {@link ErrorResponse}'s Javadoc).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateInscriptionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInscription(DuplicateInscriptionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler({FormationArchivedException.class, InvalidFormationDataException.class})
    public ResponseEntity<ErrorResponse> handleInvalidFormationRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
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
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * A business-rule 403 raised directly by a service (TICKET-029: role-based messaging
     * restrictions) — distinct from {@link AccessDeniedException} below, which is Spring
     * Security's own, thrown by {@code @PreAuthorize}'s static per-route check. See
     * {@link UnauthorizedException}'s Javadoc for why the two need separate types.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /**
     * A well-formed body {@code @Valid} can't reject on its own — a cross-field/business rule
     * (TICKET-029: {@code SendMessageRequest} needs exactly one of two mutually-exclusive shapes).
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Last-resort fallback for a UNIQUE constraint the service layer's own pre-check didn't catch
     * — e.g. two concurrent {@code POST /api/users/formateurs} for the same email both pass
     * {@code UserServiceImpl}'s {@code findByEmail} check before either commits (TICKET-019
     * review: {@link DuplicateEmailException}'s own pre-check is TOCTOU, not race-proof). Kept
     * generic (not tied to the email message specifically) since this handler is global and any
     * future unique constraint could trigger it the same way; the specific, friendlier message
     * from the pre-check is still what callers see in the overwhelmingly common case.
     *
     * <p>Narrowed to an actual unique-constraint violation (SQLState {@code 23505}) — this
     * exception also covers NOT NULL, foreign-key and check-constraint violations, which are
     * server-side bugs, not a client-side conflict. Reporting those as 409 hid them from the
     * client-facing status code *and* from the logs (TICKET-019 branch-wide review: {@code ex} was
     * never logged, so a real data bug would leave zero trace). Anything else falls through to a
     * logged 500.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isUniqueConstraintViolation(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflit : cette ressource existe déjà"));
        }
        log.error("Unexpected data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Erreur interne"));
    }

    /**
     * The {@code @Version} optimistic lock (branch-wide review) — {@code User.version}
     * (TICKET-019) and {@code Formation.version} (TICKET-022) both exist specifically to make a
     * lost-update race throw this instead of silently overwriting, but neither ticket added a
     * handler for it: {@link ObjectOptimisticLockingFailureException} extends
     * {@code ConcurrencyFailureException}, not {@link DataIntegrityViolationException}, so
     * {@link #handleDataIntegrityViolation} never saw it — it fell through to Spring Boot's
     * default {@code /error} body (an unlogged 500) instead of the documented contract.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(),
                        "Cette ressource a été modifiée entre-temps, veuillez recharger et réessayer"));
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException ex) {
        // The immediate cause, not getMostSpecificCause(): ConstraintViolationException itself
        // wraps the driver's SQLException as its own cause, so walking to the deepest cause would
        // skip straight past it to the SQLException instead.
        return ex.getCause() instanceof ConstraintViolationException cve && "23505".equals(cve.getSQLState());
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

    /**
     * {@code POST /api/formations/import} exceeding {@code spring.servlet.multipart.max-file-size}
     * (TICKET-023 review) — without this it escapes {@code @RestControllerAdvice} entirely (thrown
     * by the multipart resolver before the request reaches the dispatcher) and falls back to
     * Spring Boot's default {@code /error} body, the same {@code {status, message, details}}
     * contract gap {@link #handleMalformedBody} exists to close for bad JSON.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Fichier trop volumineux"));
    }

    /** The {@code file} multipart part is missing from a {@code POST /api/formations/import} request. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Requête invalide : fichier manquant"));
    }
}
