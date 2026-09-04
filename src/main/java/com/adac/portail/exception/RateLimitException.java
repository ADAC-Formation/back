package com.adac.portail.exception;

/**
 * Too many attempts in the current window — either too many wrong-code guesses against one token
 * ({@code attempts} on {@code ActivationToken}), or too many resend/forgot-password requests in
 * 15 minutes (see docs/DB_MODEL.md — activation_tokens). Maps to 429 (see GlobalExceptionHandler).
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
