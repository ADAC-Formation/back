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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(JwtAuthenticationIntegrationTest.PingController.class)
class JwtAuthenticationIntegrationTest {

    // RANDOM_PORT + TestRestTemplate is only needed for the two tests exercising the real
    // container's forward-to-/error behavior (see their Javadoc) — everything else in this class
    // still uses MockMvc, which is faster and sufficient for exercising the filter chain directly.
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
        // LoginAttemptService is a singleton bean shared across every test in this class (and any
        // other @SpringBootTest class reusing the same cached context) — without clearing it,
        // MockMvc's constant 127.0.0.1 means one test's failures can lock out another test's
        // email+IP key (see TICKET-045 review).
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
        // AuthenticationConfig checks the account's enabled status AFTER the password, not
        // before (TICKET-045 review) — a wrong password on a real-but-inactive account must be
        // indistinguishable from a wrong password on any other account. Getting this backwards
        // (403 with no password needed) turns "account not activated" into a free, unthrottled
        // probe for which emails are pending/suspended accounts.
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
        // Branch-wide review, live-testing finding: HttpRequestMethodNotSupportedException has no
        // handler in GlobalExceptionHandler, so it falls through to Spring Boot's default error
        // handling, which forwards internally to /error. JwtAuthorizationFilter extends
        // OncePerRequestFilter, whose shouldNotFilterErrorDispatch() is true by default, so it
        // never re-authenticates that forwarded request — but SecurityConfig's
        // .anyRequest().authenticated() still applies to the ERROR dispatch (Spring Security 6
        // default), so the entry point fires and turns a plain wrong-verb mistake into a
        // misleading 401 "Authentification requise" instead of 405. MockMvc can't reproduce this
        // — it has no real servlet container to perform the actual forward to /error — so this
        // needs a real embedded server (see restTemplate/port below), unlike every other test in
        // this class.
        String jwtCookie = loginAndGetJwtCookie(TEST_EMAIL, TEST_PASSWORD);

        // PingController only maps GET /api/test/ping.
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                url("/api/test/ping"), HttpMethod.POST, authenticatedRequest(jwtCookie), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
    }

    @Test
    void unexpectedExceptionOnAuthenticatedEndpointReturnsInternalServerErrorNotUnauthorized() {
        // Same root cause as the test above, but for the case that actually matters in
        // production: an unanticipated RuntimeException with no dedicated @ExceptionHandler also
        // forwards to /error and comes back as a misleading 401, masking a real server error as
        // an authentication problem — invisible to any monitoring keyed on status codes, and
        // confusing to debug.
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
        // Only the name=value pair is needed on the way back out — attributes (Path, HttpOnly...)
        // are for the browser, not for a Cookie request header.
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
        // Since TICKET-045, /api/auth/me isn't in SecurityConfig.PUBLIC_ROUTES, so this 401 now
        // comes from the security entry point itself (anyRequest().authenticated()) — the
        // filter chain rejects it before the request ever reaches AuthController's own
        // null-principal check, which stays only as a defense-in-depth backstop.
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
        // TICKET-045: /api/auth/logout is no longer in SecurityConfig's public list (see below),
        // so it now requires a valid cookie like any other protected endpoint.
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
        // TICKET-045: "/api/auth/**" is no longer permitAll — a path under it that isn't one of
        // the explicitly-listed public routes now hits .anyRequest().authenticated() and gets
        // 401 from the security filter chain itself, before Spring MVC would even get a chance
        // to return 404 for a route that doesn't exist. Locks in that there's no accidental
        // wildcard regression the next time a route is added under /api/auth/.
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

            // Correct password this time — must still be blocked; the lockout check runs before
            // AuthenticationManager is ever consulted (see TICKET-045 AC).
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

            // If the counter hadn't reset, this would be attempt 3+3=6 and get 429 instead.
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
        // TICKET-020. UserController.updateMe has no @PreAuthorize (see its Javadoc for why) —
        // this is the only test proving the real chain still rejects it: .anyRequest()
        // .authenticated() (SecurityConfig) covers /api/users/** since it isn't in PUBLIC_ROUTES,
        // independent of the controller's own null-principal check (which only the @WebMvcTest
        // slice, filters disabled, actually exercises).
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUserEndpointWithoutCookieReturnsUnauthorized() throws Exception {
        // TICKET-050. UserController.updateUser has @PreAuthorize("hasRole('SUPER_ADMIN')"), not
        // an absent-authentication case — same reasoning as updateMeWithoutCookieReturnsUnauthorized
        // above: only the real chain (SecurityConfig's .anyRequest().authenticated()) can prove
        // this, a @WebMvcTest slice with addFilters = false has no anonymous-authentication token
        // to evaluate hasRole(...) against.
        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messagesEndpointsWithoutCookieReturnUnauthorized() throws Exception {
        // TICKET-029. MessageController has no @PreAuthorize either (see its Javadoc) — same
        // reasoning as updateMeWithoutCookieReturnsUnauthorized above: this is the only test
        // proving SecurityConfig's .anyRequest().authenticated() actually covers /api/messages/**.
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Salut", "recipientIds", List.of(1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMeWithValidCookiePersistsEmailNotificationsEnabled() throws Exception {
        // Also the regression test for the review finding on UserServiceImpl.updateMe: mutating
        // principal.getUser() directly (loaded by CustomUserDetailsService in the filter's own
        // transaction, hence detached by the time it reaches the service) and save()-ing it would
        // merge() a pre-request snapshot — a plain Mockito unit test can't see that, only a real
        // transaction/persistence-context round trip through the actual filter chain can.
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
        // TICKET-019 branch-wide review: InscriptionRepositoryTest (@DataJpaTest) cannot actually
        // prove this — its open transaction and first-level cache mean a LAZY-proxy regression of
        // UserServiceImpl.getStagiaires would pass there too. Only a real HTTP round trip with
        // spring.jpa.open-in-view: false (the persistence context is long closed by the time this
        // response body is serialized) reproduces the LazyInitializationException this guards.
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
                // category_id is NOT NULL since V3 (TICKET-046) — any of the seeded categories
                // works, this test isn't about which one (pre-existing gap fixed in TICKET-022
                // review: this fixture predates that migration and was never updated).
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
