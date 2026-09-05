package com.adac.portail.exception;

/**
 * A referenced entity doesn't exist — a user id, a formation id, etc. Maps to 404 (see
 * docs/tech.md). First introduced by TICKET-019 (GET /api/users/{id}, and a stagiaire creation
 * request naming an unknown formation id); reused by later CRUD tickets rather than each defining
 * its own 404 exception (see docs/ARCHI.md).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
