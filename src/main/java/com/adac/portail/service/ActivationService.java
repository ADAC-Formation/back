package com.adac.portail.service;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
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
}
