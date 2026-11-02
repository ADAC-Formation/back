package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.TokenType;
import com.adac.portail.exception.ActivationTokenExpiredException;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.repository.ActivationTokenRepository;
import com.adac.portail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationServiceImpl.class);

    private static final SecureRandom CODE_RNG = new SecureRandom();
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final long MAX_CODES_PER_WINDOW = 3;
    private static final Duration RESEND_WINDOW = Duration.ofMinutes(15);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String INVALID_CODE_MESSAGE = "Code invalide ou expiré";
    private static final String RATE_LIMIT_MESSAGE = "Trop de demandes. Réessayez dans 15 minutes.";

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override

    @Transactional(noRollbackFor = ActivationTokenInvalidException.class)
    public void activate(ActivateAccountRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .filter(this::isPendingFirstActivation)
                .orElseThrow(() -> new ActivationTokenInvalidException(INVALID_CODE_MESSAGE));

        verifyAndConsumeToken(user, TokenType.ACCOUNT_ACTIVATION, request.getCode());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setActive(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendActivation(String email) {
        userRepository.findByEmail(email)
                .filter(this::isPendingFirstActivation)
                .ifPresent(user -> {
                    try {
                        issueAndEmailCode(user, TokenType.ACCOUNT_ACTIVATION);
                    } catch (MailException e) {

                        log.warn("Failed to send activation email to user id={}", user.getId());
                        log.debug("Activation email send failure detail", e);
                    }
                });
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                issueAndEmailCode(user, TokenType.PASSWORD_RESET);
            } catch (RateLimitException e) {

                log.info("Password reset code request rate-limited for a known account");
            } catch (MailException e) {

                log.warn("Failed to send password reset email to user id={}", user.getId());
                log.debug("Password reset email send failure detail", e);
            }
        });
    }

    @Override
    @Transactional(noRollbackFor = ActivationTokenInvalidException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ActivationTokenInvalidException(INVALID_CODE_MESSAGE));

        verifyAndConsumeToken(user, TokenType.PASSWORD_RESET, request.getCode());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void sendActivationCode(User user) {
        try {
            issueAndEmailCode(user, TokenType.ACCOUNT_ACTIVATION);
        } catch (MailException e) {

            log.warn("Failed to send activation email to newly created user id={}", user.getId());
            log.debug("Activation email send failure detail", e);
        }
    }

    @Override
    public boolean hasEverActivated(User user) {
        return activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(user, TokenType.ACCOUNT_ACTIVATION);
    }

    private boolean isPendingFirstActivation(User user) {
        if (user.isActive()) {
            return false;
        }
        return !hasEverActivated(user);
    }

    private void verifyAndConsumeToken(User user, TokenType type, String code) {
        ActivationToken token = activationTokenRepository
                .findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(user, type)
                .orElseThrow(() -> new ActivationTokenInvalidException(INVALID_CODE_MESSAGE));

        if (token.getAttempts() >= MAX_VERIFICATION_ATTEMPTS) {

            throw new ActivationTokenInvalidException(INVALID_CODE_MESSAGE);
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ActivationTokenExpiredException(INVALID_CODE_MESSAGE);
        }
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            activationTokenRepository.save(token);
            throw new ActivationTokenInvalidException(INVALID_CODE_MESSAGE);
        }

        token.setUsedAt(OffsetDateTime.now());
        activationTokenRepository.save(token);
    }

    private void issueAndEmailCode(User user, TokenType type) {
        OffsetDateTime windowStart = OffsetDateTime.now().minus(RESEND_WINDOW);
        long recentCount = activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(user, type, windowStart);
        if (recentCount >= MAX_CODES_PER_WINDOW) {
            throw new RateLimitException(RATE_LIMIT_MESSAGE);
        }

        String code = generateCode();
        ActivationToken token = ActivationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .type(type)
                .expiresAt(OffsetDateTime.now().plus(TOKEN_TTL))
                .build();
        activationTokenRepository.save(token);

        switch (type) {
            case ACCOUNT_ACTIVATION -> emailService.sendActivationEmail(user, code);
            case PASSWORD_RESET -> emailService.sendPasswordResetEmail(user, code);
        }
    }

    private String generateCode() {
        return String.format("%06d", CODE_RNG.nextInt(1_000_000));
    }
}
