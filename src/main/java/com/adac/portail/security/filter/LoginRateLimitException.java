package com.adac.portail.security.filter;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by {@link JwtAuthenticationFilter} when {@link com.adac.portail.security.LoginAttemptService}
 * reports the email+IP key as locked out — before {@code AuthenticationManager} is ever
 * consulted (see TICKET-045 AC). Deliberately its own type rather than Spring Security's
 * {@code LockedException}: that one means "this account is locked" (an
 * {@code AccountStatusException}, tied to {@code UserDetails.isAccountNonLocked()}), a different
 * concept from an IP+email rate limit that isn't about the account's own status.
 */
public class LoginRateLimitException extends AuthenticationException {

    public LoginRateLimitException(String message) {
        super(message);
    }
}
