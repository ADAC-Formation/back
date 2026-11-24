package com.adac.portail.dto.request;

import java.util.Map;

/**
 * Capture d'écran d'un retour, déjà validée : {@code contentType} est le type déduit de la
 * signature du fichier, jamais celui déclaré par le navigateur.
 */
public record FeedbackScreenshot(byte[] content, String contentType) {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    public String filename() {
        return "capture." + EXTENSIONS.getOrDefault(contentType, "bin");
    }
}
