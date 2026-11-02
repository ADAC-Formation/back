package com.adac.portail.exception;

public class ActivationTokenExpiredException extends RuntimeException {

    public ActivationTokenExpiredException(String message) {
        super(message);
    }
}
