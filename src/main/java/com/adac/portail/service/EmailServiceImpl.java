package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackScreenshot;
import com.adac.portail.entity.User;
import com.adac.portail.utils.EmailTemplateBuilder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String ACTIVATION_SUBJECT = "Votre code d'activation ADAC";
    private static final String PASSWORD_RESET_SUBJECT = "Réinitialisation de votre mot de passe ADAC";
    private static final String NOTIFICATION_SUBJECT = "Nouvelle notification ADAC";

    private final JavaMailSender mailSender;
    private final EmailTemplateBuilder templateBuilder;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.feedback.recipient:}")
    private String feedbackRecipient;

    @Override
    public void sendActivationEmail(User user, String code) {
        send(user.getEmail(), ACTIVATION_SUBJECT, templateBuilder.buildActivationEmail(user.getPrenom(), code));
    }

    @Override
    public void sendPasswordResetEmail(User user, String code) {
        send(user.getEmail(), PASSWORD_RESET_SUBJECT, templateBuilder.buildPasswordResetEmail(user.getPrenom(), code));
    }

    // Envoyée quel que soit emailNotificationsEnabled (NOTES-04) : le suivi Qualiopi exige que tous les comptes
    // reçoivent les notifications ; le champ reste stocké pour ne pas casser PATCH /api/users/me.
    @Override
    public void sendNotificationEmail(User user, String content) {
        send(user.getEmail(), NOTIFICATION_SUBJECT, templateBuilder.buildNotificationEmail(content));
    }

    @Override
    public void sendFeedbackEmail(User author, FeedbackRequest feedback, FeedbackScreenshot screenshot) {
        String authorName = author.getPrenom() + " " + author.getNom();
        String subject = "[ADAC][%s] %s — %s".formatted(
                feedback.type().label(), singleLine(authorName), singleLine(feedback.page()));
        String html = templateBuilder.buildFeedbackEmail(authorName, author.getRole(), feedback);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(feedbackRecipient);
            helper.setReplyTo(author.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            if (screenshot != null) {
                helper.addAttachment(screenshot.filename(),
                        new ByteArrayResource(screenshot.content()), screenshot.contentType());
            }
        } catch (MessagingException e) {
            throw new MailPreparationException("Échec de préparation de l'email de retour", e);
        }
        mailSender.send(message);
    }

    // Un retour à la ligne dans le sujet permettrait d'injecter des en-têtes (Bcc…).
    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    private void send(String to, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException e) {

            throw new MailPreparationException("Échec de préparation de l'email", e);
        }
        mailSender.send(message);
    }
}
