package com.adac.portail.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtCookieFactoryTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private JwtCookieFactory jwtCookieFactory;

    @Test
    void issueCarriesTheTokenAndTheTokensLifetime() {
        when(jwtTokenService.getExpirationMs()).thenReturn(86_400_000L);

        ResponseCookie cookie = jwtCookieFactory.issue("a-real-token");

        assertThat(cookie.getName()).isEqualTo("jwt");
        assertThat(cookie.getValue()).isEqualTo("a-real-token");
        assertThat(cookie.getMaxAge().toSeconds()).isEqualTo(86_400);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void expireSharesEveryAttributeWithIssueSoTheBrowserActuallyDeletesIt() {
        when(jwtTokenService.getExpirationMs()).thenReturn(86_400_000L);

        ResponseCookie issued = jwtCookieFactory.issue("a-real-token");
        ResponseCookie expired = jwtCookieFactory.expire();

        assertThat(expired.getName()).isEqualTo(issued.getName());
        assertThat(expired.getPath()).isEqualTo(issued.getPath());
        assertThat(expired.isHttpOnly()).isEqualTo(issued.isHttpOnly());
        assertThat(expired.isSecure()).isEqualTo(issued.isSecure());
        assertThat(expired.getSameSite()).isEqualTo(issued.getSameSite());
        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isZero();
    }

    @Test
    void secureFlagFollowsTheCookieSecureProperty() {
        when(jwtTokenService.getExpirationMs()).thenReturn(86_400_000L);
        ReflectionTestUtils.setField(jwtCookieFactory, "secureCookie", true);

        assertThat(jwtCookieFactory.issue("token").isSecure()).isTrue();
        assertThat(jwtCookieFactory.expire().isSecure()).isTrue();
    }
}
