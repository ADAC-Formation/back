package com.adac.portail.security;

import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for TICKET-006 — Spring Security + JWT cookie.
 *
 * <p>Login is handled directly by {@link com.adac.portail.security.filter.JwtAuthenticationFilter}
 * (an {@code UsernamePasswordAuthenticationFilter}), not a controller — see ARCHI.md. There is no
 * {@code AuthController} yet (that lands in TICKET-014), so this exercises the real filter chain
 * with {@code @SpringBootTest} rather than a {@code @WebMvcTest} slice.
 *
 * <p>No business endpoint exists yet to prove the "authenticated request succeeds" path, so a
 * throwaway {@link PingController} is registered just for this test class via {@code @Import}.
 *
 * <p>Runs against the real local dev PostgreSQL, like the rest of this suite (see
 * {@code FlywayMigrationTest}, {@code UserRepositoryTest}) — same convention, same trade-off.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(JwtAuthenticationIntegrationTest.PingController.class)
class JwtAuthenticationIntegrationTest {

    private static final String TEST_EMAIL = "jwt-security-test@adac.fr";
    private static final String TEST_PASSWORD = "S3cure-Pass!";
    private static final String DISABLED_EMAIL = "jwt-security-disabled@adac.fr";
    private static final String DISABLED_PASSWORD = "Also-S3cure!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deleteTestUsers();
        userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .nom("Test")
                .prenom("Jwt")
                .role(Role.STAGIAIRE)
                .build());
        userRepository.save(User.builder()
                .email(DISABLED_EMAIL)
                .passwordHash(passwordEncoder.encode(DISABLED_PASSWORD))
                .nom("Disabled")
                .prenom("Jwt")
                .role(Role.STAGIAIRE)
                .isActive(false)
                .build());
    }

    @AfterEach
    void tearDown() {
        deleteTestUsers();
    }

    private void deleteTestUsers() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(DISABLED_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void loginWithValidCredentialsSetsJwtHttpOnlyCookieAndReturnsUserResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().secure("jwt", false))
                .andExpect(cookie().sameSite("jwt", "Strict"))
                .andExpect(cookie().maxAge("jwt", 86400))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.role").value("STAGIAIRE"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("jwt"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginWithUnknownEmailReturnsUnauthorizedNotServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no-such-user@adac.fr", "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("jwt"));
    }

    @Test
    void loginWithDeactivatedAccountReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DISABLED_EMAIL, DISABLED_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist("jwt"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void loginWithMalformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(cookie().doesNotExist("jwt"));
    }

    @Test
    void protectedEndpointWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageCookieIsRejectedNotServerError() throws Exception {
        mockMvc.perform(get("/api/test/ping").cookie(new Cookie("jwt", "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidCookieIsAllowed() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");
        assertThat(jwtCookie).isNotNull();

        mockMvc.perform(get("/api/test/ping").cookie(jwtCookie))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingUserAfterCookieWasIssuedRevokesAccessOnNextRequest() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(get("/api/test/ping").cookie(jwtCookie))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/test/ping").cookie(jwtCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidCookieReturnsAuthenticatedUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(get("/api/auth/me").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.role").value("STAGIAIRE"));
    }

    @Test
    void meWithoutCookieReturnsUnauthorized() throws Exception {
        // TICKET-045 tightens SecurityConfig's PUBLIC_ROUTES ("/api/auth/**" is still permitAll
        // today) — until then, AuthController's own null-principal check is what makes this 401
        // hold; this test locks that contract in through the real filter chain regardless.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meWithGarbageCookieReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("jwt", "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutReturnsNoContentAndExpiresTheJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("jwt", ""))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().sameSite("jwt", "Strict"))
                .andExpect(cookie().path("jwt", "/"));
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    @RestController
    static class PingController {
        @GetMapping("/api/test/ping")
        ResponseEntity<String> ping() {
            return ResponseEntity.ok("pong");
        }
    }
}
