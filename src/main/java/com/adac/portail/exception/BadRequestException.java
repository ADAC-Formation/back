package com.adac.portail.exception;

/**
 * A well-formed request that {@code @Valid} bean validation can't reject on its own — a
 * cross-field rule (TICKET-029: {@code SendMessageRequest.recipientIds} required and non-empty
 * for an individual send; {@code filter} is TICKET-030's group-send path, not yet handled here).
 * Maps to 400 (see docs/tech.md). Placeholder name already reserved in docs/ARCHI.md before this
 * ticket used it.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
