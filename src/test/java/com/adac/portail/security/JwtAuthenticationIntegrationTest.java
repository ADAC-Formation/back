package com.adac.portail.security;

import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(JwtAuthenticationIntegrationTest.PingController.class)
class JwtAuthenticationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String TEST_EMAIL = "jwt-security-test@adac.fr";
    private static final String TEST_PASSWORD = "S3cure-Pass!";
    private static final String DISABLED_EMAIL = "jwt-security-disabled@adac.fr";
    private static final String DISABLED_PASSWORD = "Also-S3cure!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InscriptionRepository inscriptionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {

        loginAttemptService.reset();
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
    void loginWithWrongPasswordOnDeactivatedAccountReturnsUnauthorizedNotForbidden() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DISABLED_EMAIL, "definitely-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("jwt"))
                .andExpect(jsonPath("$.status").value(401));
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
    void wrongHttpMethodOnAuthenticatedEndpointReturnsMethodNotAllowedNotUnauthorized() {

        String jwtCookie = loginAndGetJwtCookie(TEST_EMAIL, TEST_PASSWORD);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                url("/api/test/ping"), HttpMethod.POST, authenticatedRequest(jwtCookie), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
    }

    @Test
    void unexpectedExceptionOnAuthenticatedEndpointReturnsInternalServerErrorNotUnauthorized() {

        String jwtCookie = loginAndGetJwtCookie(TEST_EMAIL, TEST_PASSWORD);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                url("/api/test/boom"), HttpMethod.GET, authenticatedRequest(jwtCookie), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Erreur interne du serveur");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String loginAndGetJwtCookie(String email, String password) {
        ResponseEntity<Void> loginResponse = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(Map.of("email", email, "password", password),
                        jsonHeaders()),
                Void.class);
        List<String> setCookieHeaders = loginResponse.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull().isNotEmpty();

        return setCookieHeaders.get(0).split(";", 2)[0];
    }

    private org.springframework.http.HttpEntity<Void> authenticatedRequest(String jwtCookie) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(org.springframework.http.HttpHeaders.COOKIE, jwtCookie);
        return new org.springframework.http.HttpEntity<>(headers);
    }

    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
    void logoutWithValidCookieReturnsNoContentAndExpiresTheJwtCookie() throws Exception {

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(post("/api/auth/logout").cookie(jwtCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("jwt", ""))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().sameSite("jwt", "Strict"))
                .andExpect(cookie().path("jwt", "/"));
    }

    @Test
    void logoutWithoutCookieReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAuthEndpointIsRejectedBeforeReachingTheDispatcher() throws Exception {

        mockMvc.perform(post("/api/auth/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsLockTheAccountAndTheSixthAttemptIsRateLimited() throws Exception {
        String email = "jwt-lockout-test@adac.fr";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .nom("Lockout")
                .prenom("Jwt")
                .role(Role.STAGIAIRE)
                .build());
        try {
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(email, "wrong-password")))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, TEST_PASSWORD)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(cookie().doesNotExist("jwt"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message").value("Trop de tentatives. Réessayez dans 15 minutes."));
        } finally {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void successfulLoginBelowTheThresholdResetsTheFailureCounter() throws Exception {
        String email = "jwt-lockout-reset-test@adac.fr";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .nom("Reset")
                .prenom("Jwt")
                .role(Role.STAGIAIRE)
                .build());
        try {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(email, "wrong-password")))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("jwt"));

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(email, "wrong-password")))
                        .andExpect(status().isUnauthorized());
            }
        } finally {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void updateMeWithoutCookieReturnsUnauthorized() throws Exception {

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUserEndpointWithoutCookieReturnsUnauthorized() throws Exception {

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messagesEndpointsWithoutCookieReturnUnauthorized() throws Exception {

        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Salut", "recipientIds", List.of(1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMeWithValidCookiePersistsEmailNotificationsEnabled() throws Exception {

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");

        mockMvc.perform(patch("/api/users/me")
                        .cookie(jwtCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("emailNotificationsEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false));

        User persisted = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(persisted.isEmailNotificationsEnabled()).isFalse();
    }

    @Test
    void getStagiairesThroughRealFilterChainReturnsFullyUsableUsersNotLazyProxies() throws Exception {

        String formateurEmail = "jwt-lazy-formateur@adac.fr";
        String formateurPassword = "F0rmateur-Pass!";
        User formateur = userRepository.save(User.builder()
                .email(formateurEmail)
                .passwordHash(passwordEncoder.encode(formateurPassword))
                .nom("Lazy")
                .prenom("Formateur")
                .role(Role.ADMIN)
                .build());
        User superAdmin = userRepository.save(User.builder()
                .email("jwt-lazy-admin@adac.fr")
                .passwordHash(passwordEncoder.encode("whatever"))
                .nom("Lazy")
                .prenom("Admin")
                .role(Role.SUPER_ADMIN)
                .build());
        Formation formation = formationRepository.save(Formation.builder()
                .intitule("Formation lazy-proxy regression test")
                .dateDebut(java.time.LocalDate.of(2026, 3, 10))
                .dateFin(java.time.LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)

                .category(categoryRepository.findAll().get(0))
                .formateur(formateur)
                .createdBy(superAdmin)
                .build());
        User stagiaire = userRepository.save(User.builder()
                .email("jwt-lazy-stagiaire@adac.fr")
                .passwordHash(passwordEncoder.encode("whatever"))
                .nom("Lazy")
                .prenom("Stagiaire")
                .role(Role.STAGIAIRE)
                .build());
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation).build());

        try {
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(formateurEmail, formateurPassword)))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie jwtCookie = loginResult.getResponse().getCookie("jwt");

            mockMvc.perform(get("/api/users/stagiaires").cookie(jwtCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].email").value("jwt-lazy-stagiaire@adac.fr"))
                    .andExpect(jsonPath("$[0].isActive").value(true));
        } finally {
            inscriptionRepository.findAllByStagiaire(stagiaire).forEach(inscriptionRepository::delete);
            formationRepository.delete(formation);
            userRepository.delete(stagiaire);
            userRepository.delete(formateur);
            userRepository.delete(superAdmin);
        }
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

        @GetMapping("/api/test/boom")
        ResponseEntity<String> boom() {
            throw new RuntimeException("boom");
        }
    }
}
