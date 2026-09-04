package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.entity.enums.TokenType;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.repository.ActivationTokenRepository;
import com.adac.portail.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code ActivationServiceImplTest} mocks {@code ActivationTokenRepository}, so it can't see
 * whether {@code attempts} actually reaches the database — Mockito doesn't simulate Spring's
 * transaction rollback. This runs against the real transaction manager (like
 * {@code JwtAuthenticationIntegrationTest}) specifically to catch that class of bug: a wrong
 * code throws inside a {@code @Transactional} method, and the default rollback-on-
 * RuntimeException would silently discard the very {@code attempts} increment the 3-guess cap
 * depends on unless the service opts out of it (see review finding on TICKET-015).
 */
@SpringBootTest
@ActiveProfiles("dev")
class ActivationServiceIntegrationTest {

    private static final String TEST_EMAIL = "activation-integration-test@adac.fr";
    private static final String CORRECT_CODE = "123456";
    private static final String WRONG_CODE = "000000";

    @Autowired
    private ActivationService activationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailSender mailSender;

    private Long tokenId;

    @BeforeEach
    void setUp() {
        deleteTestData();
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .passwordHash("irrelevant")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .isActive(false)
                .build());
        ActivationToken token = activationTokenRepository.save(ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(CORRECT_CODE))
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build());
        tokenId = token.getId();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    private void deleteTestData() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            activationTokenRepository.deleteAll(
                    activationTokenRepository.findAll().stream()
                            .filter(t -> t.getUser().getId().equals(u.getId()))
                            .toList());
            userRepository.delete(u);
        });
    }

    @Test
    void wrongCodeAttemptsSurviveTheExceptionAndPersistToTheDatabase() {
        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(TEST_EMAIL, WRONG_CODE, "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);

        // Re-fetch rather than reuse any in-memory reference: this is what proves the increment
        // actually reached the database instead of being rolled back with the exception.
        ActivationToken reloaded = activationTokenRepository.findById(tokenId).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
    }

    @Test
    void fourthAttemptIsBlockedEvenWithTheCorrectCode() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> activationService.activate(
                    new ActivateAccountRequest(TEST_EMAIL, WRONG_CODE, "N3wPassword!")))
                    .isInstanceOf(ActivationTokenInvalidException.class);
        }

        ActivationToken reloaded = activationTokenRepository.findById(tokenId).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(3);

        // Still ActivationTokenInvalidException (400), not RateLimitException (429): a distinct
        // status here would confirm a real token exists for this email — see
        // ActivationServiceImpl.verifyAndConsumeToken's Javadoc.
        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(TEST_EMAIL, CORRECT_CODE, "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);

        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(user.isActive()).isFalse();
    }
}
