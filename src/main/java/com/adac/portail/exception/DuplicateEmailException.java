package com.adac.portail.exception;

/**
 * Thrown by {@code UserServiceImpl} when creating a formateur/stagiaire with an email that
 * already belongs to another account — {@code users.email} is UNIQUE (see docs/DB_MODEL.mmd), so
 * this always mirrors a real constraint, never a race the DB alone would catch silently.
 * Maps to 409 (see docs/tech.md, POST /api/users/formateurs and /stagiaires).
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
