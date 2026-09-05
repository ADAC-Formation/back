package com.adac.portail.exception;

/**
 * The request is well-formed and the target resource exists, but performing it would violate a
 * business rule — suspending yourself, suspending the last active SUPER_ADMIN, "reactivating" an
 * account that was never activated in the first place (TICKET-019 review). Deliberately one
 * generic 409 exception rather than one per rule: {@code message} alone already distinguishes
 * them for the caller, and none of these are part of docs/tech.md's documented contract (they're
 * defensive guards the ticket review added, not tested product behavior Manon builds against).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
