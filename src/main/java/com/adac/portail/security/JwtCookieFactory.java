package com.adac.portail.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtCookieFactory {

    private static final String COOKIE_NAME = "jwt";

    private final JwtTokenService jwtTokenService;

    @Value("${jwt.cookie-secure:false}")
    private boolean secureCookie;

    public ResponseCookie issue(String token) {
        return build(token, Duration.ofMillis(jwtTokenService.getExpirationMs()));
    }

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
