package com.adac.portail.dto.response;

/**
 * The plain {@code { "message": "..." } } body several auth endpoints return on success (see
 * docs/tech.md — activate, resend-activation, forgot-password, reset-password). Not reused for
 * chat messages — see {@link MessageResponse} for that, unrelated domain.
 */
public record StatusMessageResponse(String message) {
}
