package com.adac.portail.service;

import com.adac.portail.entity.User;
import org.springframework.mail.MailException;

public interface EmailService {

    void sendActivationEmail(User user, String code);

    void sendPasswordResetEmail(User user, String code);

    void sendNotificationEmail(User user, String content);
}
