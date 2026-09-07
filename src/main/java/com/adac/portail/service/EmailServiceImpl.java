package com.adac.portail.service;

import com.adac.portail.entity.User;
import com.adac.portail.utils.EmailTemplateBuilder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** See {@link EmailService} for scope. */
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

    @Override
    public void sendActivationEmail(User user, String code) {
        send(user.getEmail(), ACTIVATION_SUBJECT, templateBuilder.buildActivationEmail(user.getPrenom(), code));
    }

    @Override
    public void sendPasswordResetEmail(User user, String code) {
        send(user.getEmail(), PASSWORD_RESET_SUBJECT, templateBuilder.buildPasswordResetEmail(user.getPrenom(), code));
    }

    @Override
    public void sendNotificationEmail(User user, String content) {
        if (!user.isEmailNotificationsEnabled()) {
            return;
        }
        send(user.getEmail(), NOTIFICATION_SUBJECT, templateBuilder.buildNotificationEmail(content));
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
            // MimeMessageHelper's checked exception, wrapped as Spring's own unchecked MailException
            // subtype — callers (ActivationServiceImpl) only ever need to catch MailException, the
            // same as they already do for a send()-time SMTP failure (see this class's Javadoc).
            throw new MailPreparationException("Échec de préparation de l'email", e);
        }
        mailSender.send(message);
    }
}
