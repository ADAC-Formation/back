package com.adac.portail.dto.request;

/**
 * Champs texte d'un retour ({@code POST /api/feedback}). Le contexte (page, navigateur, taille de
 * fenêtre, version) est fourni par le front à titre indicatif ; l'identité de l'auteur, elle, vient
 * toujours de la session et ne fait pas partie de cette requête.
 */
public record FeedbackRequest(
        FeedbackType type,
        String message,
        String page,
        String userAgent,
        String viewport,
        String appVersion) {
}
