package com.adac.portail.exception;

/** The most recent unused token for this user+type exists but {@code expiresAt} is in the past. */
public class ActivationTokenExpiredException extends RuntimeException {

    public ActivationTokenExpiredException(String message) {
        super(message);
    }
}
