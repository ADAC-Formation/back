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
     * @throws ActivationTokenInvalidException no unused token for this email/type, or the code
     *                                          doesn't match the current one
     * @throws ActivationTokenExpiredException  the current token exists but is past its TTL
     * @throws RateLimitException               too many wrong-code guesses against the current
     *                                           token (see {@code ActivationToken.attempts})
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
     * @throws ActivationTokenInvalidException no unused token for this email/type, or the code
     *                                          doesn't match the current one
     * @throws ActivationTokenExpiredException  the current token exists but is past its TTL
     * @throws RateLimitException               too many wrong-code guesses against the current
     *                                           token
     */
    void resetPassword(ResetPasswordRequest request);
}
