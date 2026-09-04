package com.adac.portail.exception;

/**
 * No usable token for this user+type (never existed, or the only ones are already used), or the
 * submitted code doesn't match the current one. docs/tech.md gives this the same wire message as
 * {@link ActivationTokenExpiredException} — deliberately: distinguishing "expired" from "wrong
 * code" to the caller would help an attacker enumerate valid emails/tokens.
 */
public class ActivationTokenInvalidException extends RuntimeException {

    public ActivationTokenInvalidException(String message) {
        super(message);
    }
}
