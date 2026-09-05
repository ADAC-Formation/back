package com.adac.portail.exception;

/**
 * 404 — no resource with the given id. Generic across entities (the message carries which one),
 * matching the name already established on {@code feature/users} for the same purpose — kept
 * identical here so the two branches don't end up with two classes for the same thing once merged.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
