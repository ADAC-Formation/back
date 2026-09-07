package com.adac.portail.service;

import com.adac.portail.entity.User;
import org.springframework.mail.MailException;

/**
 * TICKET-034 — HTML transactional and notification emails, replacing {@code ActivationServiceImpl}'s
 * previous direct {@code MailSender}/{@code SimpleMailMessage} plain-text usage (see its Javadoc).
 */
public interface EmailService {

    /**
     * Always sent, independent of {@code user.isEmailNotificationsEnabled()} — a transactional
     * email, not an in-app-notification email (docs/tickets/TICKET-034.md AC).
     *
     * @throws MailException on an SMTP-level failure — deliberately not swallowed here; whether a
     *                        failed send should block the caller is that caller's decision (see
     *                        {@code ActivationServiceImpl}'s own {@code catch (MailException e)}
     *                        blocks and their oracle-avoidance reasoning).
     */
    void sendActivationEmail(User user, String code);

    /** Same contract as {@link #sendActivationEmail}, for the password-reset code. */
    void sendPasswordResetEmail(User user, String code);

    /** No-op when {@code user.isEmailNotificationsEnabled()} is {@code false} — never throws in that case. */
    void sendNotificationEmail(User user, String content);
}
