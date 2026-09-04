package com.adac.portail.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Single source of truth for the {@code jwt} cookie's shape (see CLAUDE.md — Auth section).
 *
 * <p>Login ({@link com.adac.portail.security.filter.JwtAuthenticationFilter}) and logout
 * ({@link com.adac.portail.controller.AuthController}) both need to produce a {@code Set-Cookie}
 * for the same cookie — a browser only deletes a cookie on logout if every attribute (path,
 * domain, secure, sameSite) matches the one that set it. Building both here, from one place,
 * makes that impossible to drift apart (see review finding on TICKET-014).</p>
 */
@Component
@RequiredArgsConstructor
public class JwtCookieFactory {

    private static final String COOKIE_NAME = "jwt";

    private final JwtTokenService jwtTokenService;

    @Value("${jwt.cookie-secure:false}")
    private boolean secureCookie;

    /** The cookie a successful login poses, carrying {@code token}, valid for the JWT's lifetime. */
    public ResponseCookie issue(String token) {
        return build(token, Duration.ofMillis(jwtTokenService.getExpirationMs()));
    }

    /** The cookie logout poses to expire the one login set (same attributes, {@code maxAge=0}). */
    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .maxAge(maxAge)
                .path("/")
                .build();
    }
}
