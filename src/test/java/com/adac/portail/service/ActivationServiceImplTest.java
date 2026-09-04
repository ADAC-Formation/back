package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.entity.enums.TokenType;
import com.adac.portail.exception.ActivationTokenExpiredException;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.repository.ActivationTokenRepository;
import com.adac.portail.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivationTokenRepository activationTokenRepository;

    @Mock
    private MailSender mailSender;

    // Real BCrypt, not mocked: the service hashes/matches the 6-digit code with it, and a mock
    // encoder that just echoes its input would let a wrong-code test pass for the wrong reason.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ActivationServiceImpl activationService;

    private User user;

    @BeforeEach
    void setUp() {
        activationService = new ActivationServiceImpl(userRepository, activationTokenRepository,
                passwordEncoder, mailSender);
        ReflectionTestUtils.setField(activationService, "fromAddress", "no-reply@adac.fr");

        user = User.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .passwordHash("old-hash")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .isActive(false)
                .build();
    }

    // --- activate -----------------------------------------------------------------------

    @Test
    void activateWithValidCodeActivatesUserAndConsumesToken() {
        String code = "123456";
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .attempts(0)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        activationService.activate(new ActivateAccountRequest(user.getEmail(), code, "N3wPassword!"));

        assertThat(user.isActive()).isTrue();
        assertThat(passwordEncoder.matches("N3wPassword!", user.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(activationTokenRepository).save(token);
        verify(userRepository).save(user);
    }

    @Test
    void activateWithExpiredTokenThrowsExpiredException() {
        String code = "123456";
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .attempts(0)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(user.getEmail(), code, "N3wPassword!")))
                .isInstanceOf(ActivationTokenExpiredException.class);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void activateWithNoUnusedTokenThrowsInvalidException() {
        // Covers both "never requested" and "already used" (the repo query already filters
        // usedAt IS NULL, so a used token surfaces as Optional.empty() here too).
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(user.getEmail(), "123456", "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);
    }

    @Test
    void activateWithWrongCodeThrowsInvalidExceptionAndIncrementsAttempts() {
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode("123456"))
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .attempts(0)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(user.getEmail(), "000000", "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);

        assertThat(token.getAttempts()).isEqualTo(1);
        assertThat(user.isActive()).isFalse();
        verify(activationTokenRepository).save(token);
    }

    @Test
    void activateAfterMaxAttemptsThrowsInvalidExceptionWithoutCheckingTheCode() {
        // 400, not 429: a distinct "too many attempts" status would itself confirm a real token
        // exists for this email, once attempts actually persists (see the @Transactional fix) —
        // see verifyAndConsumeToken's Javadoc.
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode("123456"))
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .attempts(3)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.ACCOUNT_ACTIVATION)).thenReturn(Optional.of(token));

        // Right code this time — must still be blocked, the lockout doesn't care.
        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(user.getEmail(), "123456", "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void activateForSuspendedAccountThrowsInvalidExceptionWithoutTouchingAnyToken() {
        // Suspended (isActive=false, but already activated once before) must not be reactivatable
        // by replaying an old, still-unexpired activation code directly against /activate.
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(user, TokenType.ACCOUNT_ACTIVATION))
                .thenReturn(true);

        assertThatThrownBy(() -> activationService.activate(
                new ActivateAccountRequest(user.getEmail(), "123456", "N3wPassword!")))
                .isInstanceOf(ActivationTokenInvalidException.class);

        assertThat(user.isActive()).isFalse();
        verify(activationTokenRepository, never())
                .findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    // --- resendActivation -----------------------------------------------------------------

    @Test
    void resendActivationWithinLimitCreatesNewTokenAndSendsEmail() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.ACCOUNT_ACTIVATION), any())).thenReturn(1L);

        activationService.resendActivation(user.getEmail());

        ArgumentCaptor<ActivationToken> captor = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TokenType.ACCOUNT_ACTIVATION);
        assertThat(captor.getValue().getExpiresAt()).isAfter(OffsetDateTime.now().plusMinutes(29));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendActivationOverLimitThrowsRateLimitExceptionAndSendsNothing() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.ACCOUNT_ACTIVATION), any())).thenReturn(3L);

        assertThatThrownBy(() -> activationService.resendActivation(user.getEmail()))
                .isInstanceOf(RateLimitException.class);

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendActivationWithUnknownEmailDoesNothingAndDoesNotThrow() {
        when(userRepository.findByEmail("nobody@adac.fr")).thenReturn(Optional.empty());

        activationService.resendActivation("nobody@adac.fr");

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendActivationForSuspendedAccountDoesNothing() {
        // isActive=false but a used ACCOUNT_ACTIVATION token exists -> this user was activated
        // once and later suspended, not a new pending account (see isPendingFirstActivation).
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(user, TokenType.ACCOUNT_ACTIVATION))
                .thenReturn(true);

        activationService.resendActivation(user.getEmail());

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendActivationForAlreadyActiveAccountDoesNothing() {
        user.setActive(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        activationService.resendActivation(user.getEmail());

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // --- forgotPassword ---------------------------------------------------------------------

    @Test
    void forgotPasswordWithKnownEmailCreatesTokenAndSendsEmail() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.PASSWORD_RESET), any())).thenReturn(0L);

        activationService.forgotPassword(user.getEmail());

        verify(activationTokenRepository).save(any(ActivationToken.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPasswordOverLimitSwallowsRateLimitAndReturnsNormally() {
        // Unlike resend-activation, this endpoint's contract (docs/tech.md) is the same response
        // no matter what — a 429 here would tell an attacker the email exists AND is currently
        // rate-limited. Must not throw, and must not send.
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.PASSWORD_RESET), any())).thenReturn(3L);

        activationService.forgotPassword(user.getEmail());

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPasswordSwallowsMailExceptionInsteadOfReturning500() {
        // Same reasoning as the rate-limit case: an SMTP-level failure must not turn into a
        // response difference between a known and an unknown email.
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                eq(user), eq(TokenType.PASSWORD_RESET), any())).thenReturn(0L);
        doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

        activationService.forgotPassword(user.getEmail());

        verify(activationTokenRepository).save(any(ActivationToken.class));
    }

    @Test
    void forgotPasswordWithUnknownEmailDoesNothingSilently() {
        when(userRepository.findByEmail("nobody@adac.fr")).thenReturn(Optional.empty());

        activationService.forgotPassword("nobody@adac.fr");

        verify(activationTokenRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // --- resetPassword ----------------------------------------------------------------------

    @Test
    void resetPasswordWithValidCodeUpdatesPasswordAndConsumesToken() {
        String code = "654321";
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .type(TokenType.PASSWORD_RESET)
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .attempts(0)
                .build();
        String oldHash = user.getPasswordHash();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));

        activationService.resetPassword(new ResetPasswordRequest(user.getEmail(), code, "An0therPass!"));

        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("An0therPass!", user.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordWithExpiredTokenThrowsExpiredExceptionAndLeavesPasswordUnchanged() {
        String oldHash = user.getPasswordHash();
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode("654321"))
                .type(TokenType.PASSWORD_RESET)
                .expiresAt(OffsetDateTime.now().minusSeconds(1))
                .attempts(0)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(activationTokenRepository.findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
                user, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> activationService.resetPassword(
                new ResetPasswordRequest(user.getEmail(), "654321", "An0therPass!")))
                .isInstanceOf(ActivationTokenExpiredException.class);
        assertThat(user.getPasswordHash()).isEqualTo(oldHash);
    }
}
