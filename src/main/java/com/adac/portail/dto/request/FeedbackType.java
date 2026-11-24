package com.adac.portail.dto.request;

/** Nature d'un retour envoyé depuis le bouton « Envoyer un retour ». */
public enum FeedbackType {
    BUG("Bug"),
    SUGGESTION("Suggestion");

    private final String label;

    FeedbackType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
