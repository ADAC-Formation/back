package com.adac.portail.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Generates and verifies the JWT carried in the {@code jwt} HttpOnly cookie (see CLAUDE.md —
 * Auth section, and ARCHI.md — Authentification).
 *
 * <p>Only {@link #verify(String)} decodes a token — it always checks the signature first, so
 * there is no method that hands back claims from an unverified token.</p>
 */
@Service
public class JwtTokenService {

    private final Algorithm algorithm;
    private final long expirationMs;

    public JwtTokenService(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long expirationMs) {
        // HS256 with a short secret is offline-crackable — fail fast rather than sign tokens an
        // attacker could forge (see review finding on TICKET-006).
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 256 bits (32 bytes) — see .env.example");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(userDetails.getUsername())
                .withIssuedAt(now)
                .withExpiresAt(now.plusMillis(expirationMs))
                .sign(algorithm);
    }

    /**
     * Verifies the token's signature and expiration. Empty means invalid or expired — never
     * decode claims from a token without going through this first.
     */
    public Optional<DecodedJWT> verify(String token) {
        try {
            return Optional.of(JWT.require(algorithm).build().verify(token));
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
