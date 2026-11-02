package com.adac.portail.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String VALID_SECRET = "a".repeat(32);
    private static final UserDetails USER = User.withUsername("user@adac.fr").password("x").authorities("ROLE_STAGIAIRE").build();

    @Test
    void constructorRejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtTokenService("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void constructorRejectsNullSecret() {
        assertThatThrownBy(() -> new JwtTokenService(null, 60_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatedTokenVerifiesAndCarriesTheUsernameAsSubject() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 60_000);

        String token = service.generateToken(USER);
        Optional<DecodedJWT> decoded = service.verify(token);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().getSubject()).isEqualTo("user@adac.fr");
    }

    @Test
    void tamperedTokenFailsVerification() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 60_000);
        String token = service.generateToken(USER);

        int mid = token.length() / 2;
        char flipped = token.charAt(mid) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mid) + flipped + token.substring(mid + 1);

        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretFailsVerification() {
        JwtTokenService issuer = new JwtTokenService(VALID_SECRET, 60_000);
        JwtTokenService verifier = new JwtTokenService("b".repeat(32), 60_000);

        String token = issuer.generateToken(USER);

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void expiredTokenFailsVerification() throws InterruptedException {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 1);
        String token = service.generateToken(USER);

        Thread.sleep(50);
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    void garbageStringFailsVerification() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 60_000);

        assertThat(service.verify("not-a-jwt-at-all")).isEmpty();
    }
}
