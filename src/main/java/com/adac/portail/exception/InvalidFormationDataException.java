package com.adac.portail.exception;

/**
 * The request body for a formation create/update is well-formed JSON but references or contains
 * invalid data the bean-validation layer can't catch on its own — a missing/unknown
 * {@code categoryId} (docs/tech.md: "400 — categoryId manquant ou introuvable") or a merged
 * {@code dateFin} before {@code dateDebut} on a partial update. Deliberately one exception for
 * both rather than one per case (same reasoning as {@link ConflictException}): {@code message}
 * alone already distinguishes them for the caller.
 */
public class InvalidFormationDataException extends RuntimeException {
    public InvalidFormationDataException(String message) {
        super(message);
    }
}
