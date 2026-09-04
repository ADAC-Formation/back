package com.adac.portail.controller;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController} — TICKET-014.
 *
 * <p>Login itself is exercised end-to-end by {@code JwtAuthenticationIntegrationTest}: it is
 * handled directly by {@code JwtAuthenticationFilter}, not this controller (see ARCHI.md), so it
 * isn't re-tested here. This class only covers {@code /logout} and {@code /me}'s own logic;
 * {@code JwtAuthenticationIntegrationTest} additionally exercises both through the real filter
 * chain (cookie in, cookie/401 out) — this class alone can't prove {@code /me} stays behind auth
 * once {@code SecurityConfig} stops permitting {@code /api/auth/**} (TICKET-045).
 *
 * <p>Security filters are disabled ({@code addFilters = false}) so {@code /me}'s own
 * null-principal 401 check is exercised directly, independent of the real filter chain — which
 * also means the authenticated case below sets {@link SecurityContextHolder} by hand rather than
 * via {@code SecurityMockMvcRequestPostProcessors.authentication(...)}: that helper relies on the
 * very filter ({@code SecurityContextHolderFilter}) that {@code addFilters = false} skips.</p>
 *
 * <p>{@code JwtAuthorizationFilter} is a {@code @Component} implementing {@code Filter}, so
 * {@code @WebMvcTest} still picks it up as a bean (even with {@code addFilters = false}, which
 * only skips registering it in the mock chain) — its own dependencies are mocked below purely to
 * satisfy that construction, they're never exercised.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtCookieFactory jwtCookieFactory;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutReturnsNoContentAndExpiresJwtCookie() throws Exception {
        ResponseCookie expired = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
        when(jwtCookieFactory.expire()).thenReturn(expired);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("jwt", ""))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().secure("jwt", false))
                .andExpect(cookie().sameSite("jwt", "Strict"))
                .andExpect(cookie().path("jwt", "/"));
    }

    @Test
    void meWithAuthenticatedPrincipalReturnsUserResponse() throws Exception {
        User user = User.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build();
        AdacUserDetails principal = new AdacUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        when(authService.getCurrentUser(eq(principal))).thenReturn(UserResponse.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("stagiaire@adac.fr"))
                .andExpect(jsonPath("$.role").value("STAGIAIRE"));
    }

    @Test
    void meWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
