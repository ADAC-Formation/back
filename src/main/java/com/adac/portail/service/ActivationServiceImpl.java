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

/**
 * See docs/PRD.md § TokenActivation and docs/DB_MODEL.md § activation_tokens for the numbers
 * used here (30 min TTL, 3 wrong-code guesses per token, 3 new codes per 15 min).
 *
 * <p>Sends HTML mail via {@link EmailService} (TICKET-034) — this class used to build a
 * plain-text {@code SimpleMailMessage} directly via {@code MailSender} before that service
 * existed; {@link EmailService#sendActivationEmail}/{@link EmailService#sendPasswordResetEmail}
 * still throw {@link MailException} on an SMTP-level failure exactly as the old direct {@code
 * mailSender.send(...)} call did, so every {@code catch (MailException e)} block below and its
 * oracle-avoidance reasoning carries over unchanged — only the body (plain text → ADAC-branded
 * HTML) and the call site moved.</p>
 */
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
    // Wrong-code guesses must survive the exception they raise (see verifyAndConsumeToken) or the
    // 3-guess cap can never trip — Spring's default rollback-on-RuntimeException would otherwise
    // discard the very `attempts` increment the check depends on (see TICKET-015 review).
    @Transactional(noRollbackFor = ActivationTokenInvalidException.class)
    public void activate(ActivateAccountRequest request) {
        // Same guard as resendActivation, and for the same reason: without it, a suspended user
        // who still holds an old, not-yet-expired activation code (issued before they were
        // suspended) could reactivate themselves directly, skipping resendActivation entirely
        // (see TICKET-015 review).
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
                        // Unlike RateLimitException (left to propagate — this endpoint's own AC
                        // wants the 429 visible), an SMTP failure must not turn into a 500 for a
                        // known, pending email while an unknown one keeps getting 200 — same
                        // oracle forgotPassword guards against, through the same shared method
                        // (see TICKET-045 branch-wide review — this endpoint was missing it).
                        // Logs the id, not `e` at WARN (branch-wide review): MailSendException's
                        // own message embeds the failed recipient address, which would otherwise
                        // contradict this class's own "log the id, not the email" policy (see
                        // sendActivationCode's comment) the moment a send actually fails.
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
                // Never let this endpoint's response depend on account state — docs/tech.md
                // requires the exact same 200 whether the email is unknown, known, or currently
                // rate-limited; leaking the latter is exactly the account-enumeration oracle this
                // endpoint exists to prevent (see TICKET-015 review). resend-activation is
                // different: its own ticket AC deliberately wants the 429 visible.
                log.info("Password reset code request rate-limited for a known account");
            } catch (MailException e) {
                // Same reasoning, different failure mode: an SMTP-level rejection must not turn
                // into a 500 for known emails while unknown ones keep getting 200 — that's the
                // same oracle through a different door (see TICKET-015 review). Logs the id, not
                // `e` at WARN — see resendActivation's equivalent comment.
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
            // Same reasoning as resendActivation: an SMTP failure must not blow up account
            // creation, which already succeeded (the row is saved) by the time this runs. Logs
            // the id, not the email (TICKET-019 review) — same choice as the other two catch
            // blocks in this class. `e` itself goes to DEBUG, not this WARN: MailSendException's
            // message embeds the failed recipient address, which would leak the email into the
            // WARN line's rendered output even though the format string above never names it
            // (branch-wide review, TICKET-034).
            log.warn("Failed to send activation email to newly created user id={}", user.getId());
            log.debug("Activation email send failure detail", e);
        }
    }

    @Override
    public boolean hasEverActivated(User user) {
        return activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(user, TokenType.ACCOUNT_ACTIVATION);
    }

    /**
     * An already-activated user has no legitimate reason to request a fresh
     * {@code ACCOUNT_ACTIVATION} code, and — critically — {@code User.isActive} doubles as both
     * "never activated yet" and "suspended by an admin" (see docs/DB_MODEL.md — no separate
     * status), so {@code isActive() == false} alone can't tell those apart. Whether this user has
     * *ever* consumed one before can: if they have, {@code false} today means suspended, not
     * pending — and resend-activation must not hand a suspended account a way back in (see
     * TICKET-015 review). Silently doing nothing here (same as an unknown email) avoids leaking
     * which case applies.
     */
    private boolean isPendingFirstActivation(User user) {
        if (user.isActive()) {
            return false;
        }
        return !hasEverActivated(user);
    }

    /**
     * Shared by {@code activate} and {@code resetPassword}: fetch the most recent unused token,
     * enforce the per-token guess limit, then either consume it or record a failed guess.
     *
     * <p>The repository fetch below takes a {@code PESSIMISTIC_WRITE} lock on the token row —
     * without it, two concurrent guesses against the same token both read {@code attempts} before
     * either commits, so both count as guess #1 and the 3-guess cap can be bypassed by throwing
     * requests at it in parallel (see TICKET-015 review). The lock makes them queue instead.</p>
     */
    private void verifyAndConsumeToken(User user, TokenType type, String code) {
        ActivationToken token = activationTokenRepository
                .findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(user, type)
                .orElseThrow(() -> new ActivationTokenInvalidException(INVALID_CODE_MESSAGE));

        if (token.getAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            // Same wire shape as a wrong code, deliberately: once guesses are exhausted, telling
            // the caller "429, too many attempts" vs "400, invalid code" would itself confirm a
            // real token exists for this email — turning /activate and /reset-password into an
            // account-enumeration oracle once `attempts` actually persists (see fix for the
            // @Transactional rollback bug, TICKET-015 review). The 429 that's still visible on
            // resend-activation is a deliberate, narrower exception — see forgotPassword's
            // Javadoc and docs/tech.md.
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

    /** Shared by {@code resendActivation}, {@code forgotPassword} and {@code sendActivationCode}: rate-limit, then issue + email a new code. */
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

        // Exhaustive switch, not if/else (branch-wide review): an if/else's implicit `else`
        // would silently route any future third TokenType through sendPasswordResetEmail,
        // mailing a real credential under the wrong template. A missing case here is a compile
        // error instead.
        switch (type) {
            case ACCOUNT_ACTIVATION -> emailService.sendActivationEmail(user, code);
            case PASSWORD_RESET -> emailService.sendPasswordResetEmail(user, code);
        }
    }

    /** A uniformly-random 6-digit code, zero-padded (e.g. "004213") — {@link SecureRandom}, not
     * {@link java.util.Random}: this is a credential, not a UI detail. */
    private String generateCode() {
        return String.format("%06d", CODE_RNG.nextInt(1_000_000));
    }
}
