package com.adac.portail.exception;

/**
 * Too many new activation/reset codes issued for one user in 15 minutes (the *creation* limit —
 * see {@code ActivationTokenRepository.countByUserAndTypeAndCreatedAtAfter}), maps to 429 (see
 * {@code GlobalExceptionHandler}).
 *
 * <p><b>Not</b> used for exhausted wrong-code guesses against an existing token — that's
 * {@link ActivationTokenInvalidException} (400) on purpose, to avoid an account-enumeration
 * oracle (see {@code ActivationServiceImpl.verifyAndConsumeToken}'s Javadoc). And {@code
 * ActivationServiceImpl.forgotPassword} catches this exception itself rather than letting it
 * reach {@code GlobalExceptionHandler} — that endpoint's contract is the same 200 regardless of
 * rate-limit state; only {@code resendActivation} lets it surface as a real 429.</p>
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
