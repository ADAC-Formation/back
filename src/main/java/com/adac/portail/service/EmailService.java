package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackScreenshot;
import com.adac.portail.entity.User;
import org.springframework.mail.MailException;

public interface EmailService {

    void sendActivationEmail(User user, String code);

    void sendPasswordResetEmail(User user, String code);

    void sendNotificationEmail(User user, String content);

    /**
     * Envoie un retour au destinataire configuré ({@code app.feedback.recipient}), avec l'auteur
     * en adresse de réponse. {@code screenshot} peut être {@code null}.
     */
    void sendFeedbackEmail(User author, FeedbackRequest feedback, FeedbackScreenshot screenshot);
}
