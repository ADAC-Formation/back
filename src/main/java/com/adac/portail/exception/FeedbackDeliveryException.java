package com.adac.portail.exception;

/**
 * L'email d'un retour n'a pas pu partir (serveur de mail indisponible, destinataire non configuré…).
 * Traduite en 503 pour ce seul endpoint : les autres envois d'email gardent leur comportement.
 */
public class FeedbackDeliveryException extends RuntimeException {

    public static final String MESSAGE = "Envoi impossible pour le moment, réessayez plus tard.";

    public FeedbackDeliveryException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
