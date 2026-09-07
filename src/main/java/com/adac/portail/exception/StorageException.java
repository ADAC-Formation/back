package com.adac.portail.exception;

/**
 * Wraps a Supabase Storage failure (network error, non-2xx response, unreadable multipart bytes)
 * — branch-wide review: previously these escaped {@code StorageServiceImpl} unmapped and fell
 * through to Spring Boot's default {@code /error} body instead of docs/tech.md's
 * {@code {status, message, details}} contract. Maps to 502 in {@link GlobalExceptionHandler} — an
 * infrastructure failure, not a client error.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
