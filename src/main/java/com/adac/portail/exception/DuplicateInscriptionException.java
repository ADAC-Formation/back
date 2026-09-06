package com.adac.portail.exception;

/**
 * Thrown when enrolling a stagiaire already enrolled in the target formation —
 * {@code uk_inscriptions_stagiaire_formation} (see {@code Inscription}) always mirrors a real
 * constraint, never a race the DB alone would catch silently. Maps to 409 (docs/tech.md,
 * POST /api/formations/{id}/inscriptions).
 */
public class DuplicateInscriptionException extends RuntimeException {

    public DuplicateInscriptionException(String message) {
        super(message);
    }
}
