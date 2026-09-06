package com.adac.portail.exception;

/**
 * A business-rule authorization refusal — distinct from Spring Security's own
 * {@code AccessDeniedException} (thrown by {@code @PreAuthorize}, a static per-route role check):
 * this is for a data-dependent rule that can't be expressed as a route-level annotation, e.g. "a
 * STAGIAIRE may message a SUPER_ADMIN or an active ADMIN, but no one else" (TICKET-029). Maps to
 * 403 (see docs/tech.md), same as {@code AccessDeniedException}. Placeholder name already reserved
 * in docs/ARCHI.md before this ticket used it.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
