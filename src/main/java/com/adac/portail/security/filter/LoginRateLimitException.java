package com.adac.portail.security.filter;

import org.springframework.security.core.AuthenticationException;

public class LoginRateLimitException extends AuthenticationException {

    public LoginRateLimitException(String message) {
        super(message);
    }
}
