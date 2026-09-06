package com.adac.portail.exception;

/**
 * A formation with {@code status = ARCHIVED} was targeted by a write it no longer accepts —
 * archiving is irreversible (see docs/tech.md, TICKET-022). Maps to 400, not 409: the request
 * itself is well-formed and the resource exists, but its own state (not a conflicting concurrent
 * write) is what makes the write invalid, matching docs/tech.md's documented 400 for this case.
 */
public class FormationArchivedException extends RuntimeException {
    public FormationArchivedException(String message) {
        super(message);
    }
}
