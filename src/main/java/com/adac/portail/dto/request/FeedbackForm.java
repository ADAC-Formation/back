package com.adac.portail.dto.request;

/**
 * Champs texte bruts du formulaire multipart {@code POST /api/feedback}, avant validation par
 * {@code FeedbackService}. Aucune partie d'identité (email, rôle) n'y figure : une partie de ce nom
 * ajoutée par le client n'est liée à rien et reste ignorée.
 */
public record FeedbackForm(
        String type,
        String message,
        String page,
        String userAgent,
        String viewport,
        String appVersion) {
}
