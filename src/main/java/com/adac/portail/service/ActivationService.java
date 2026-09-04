package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
import com.adac.portail.entity.User;
import com.adac.portail.exception.ActivationTokenExpiredException;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.exception.RateLimitException;

/**
 * Account activation and password reset — both driven by the same {@code activation_tokens}
 * table (see docs/DB_MODEL.md), distinguished by {@code TokenType}.
 */
public interface ActivationService {

    /**
     * @throws ActivationTokenInvalidException no unused token for this email/type, the code
     *                                          doesn't match the current one, or — deliberately
     *                                          the same exception, not {@link RateLimitException}
     *                                          — the current token's guesses are exhausted (see
     *                                          {@code ActivationToken.attempts}); a distinct 429
     *                                          here would confirm a real token exists for this
     *                                          email, see impl Javadoc
     * @throws ActivationTokenExpiredException  the current token exists but is past its TTL
     */
    void activate(ActivateAccountRequest request);

    /**
     * Silently does nothing for an unknown email — same reasoning as {@link #forgotPassword},
     * see its Javadoc.
     *
     * @throws RateLimitException too many resend requests for this email in the last 15 minutes
     */
    void resendActivation(String email);

    /**
     * Always succeeds from the caller's point of view, known email or not — docs/tech.md
     * requires the same response either way, so an attacker can't use this endpoint to test
     * which emails have an account.
     */
    void forgotPassword(String email);

    /**
     * @throws ActivationTokenInvalidException no unused token for this email/type, the code
     *                                          doesn't match the current one, or the current
     *                                          token's guesses are exhausted — see
     *                                          {@link #activate} for why that's not
     *                                          {@link RateLimitException} here
     * @throws ActivationTokenExpiredException  the current token exists but is past its TTL
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Issues the very first {@code ACCOUNT_ACTIVATION} code for a just-created formateur/stagiaire
     * and emails it — called once by {@code UserServiceImpl} right after {@code userRepository
     * .save(user)} (TICKET-019, docs/STORIES.md US-007/US-008 AC-02). Unlike {@link
     * #resendActivation}, there's no {@link RateLimitException} risk here: a brand-new user has
     * issued zero codes so far, so the shared 3-per-15-min check always passes on this first call.
     *
     * <p>An SMTP failure is swallowed (logged, not thrown) rather than failing the whole account
     * creation — same reasoning as {@link #resendActivation}'s Javadoc, just without that
     * endpoint's account-enumeration angle (the caller here is an authenticated SUPER_ADMIN, not
     * an anonymous prober).</p>
     */
    void sendActivationCode(User user);

    /**
     * Has this user ever completed an {@code ACCOUNT_ACTIVATION}? Used by
     * {@code UserServiceImpl.reactivate} (TICKET-019 review) to refuse "reactivating" an account
     * that was never activated in the first place — {@code User.isActive} alone can't tell
     * "pending" from "suspended" apart (see {@code isPendingFirstActivation}'s Javadoc); silently
     * flipping a pending account to {@code isActive=true} here would leave it holding its
     * original random, never-communicated password hash while also making it permanently
     * ineligible for {@code /activate} and {@code /resend-activation} (both gated on
     * {@code isPendingFirstActivation}), i.e. an account nobody can ever log into.
     */
    boolean hasEverActivated(User user);
}
