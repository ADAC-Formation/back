package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackForm;
import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackScreenshot;
import com.adac.portail.dto.request.FeedbackType;
import com.adac.portail.entity.User;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.FeedbackDeliveryException;
import com.adac.portail.utils.FeedbackScreenshotValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    static final int MIN_MESSAGE_LENGTH = 10;
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int MAX_CONTEXT_LENGTH = 500;

    private static final String INVALID_TYPE_MESSAGE = "Type de retour invalide (Bug ou Suggestion)";
    private static final String INVALID_MESSAGE_LENGTH =
            "Le message doit contenir entre " + MIN_MESSAGE_LENGTH + " et " + MAX_MESSAGE_LENGTH + " caractères";

    private final EmailService emailService;
    private final FeedbackRateLimiter rateLimiter;
    private final FeedbackScreenshotValidator screenshotValidator;

    @Override
    public void send(User author, FeedbackForm form, MultipartFile screenshot) {
        rateLimiter.checkAllowed(author.getId());
        FeedbackRequest feedback = toFeedback(form);
        FeedbackScreenshot attachment = toAttachment(screenshot);

        try {
            emailService.sendFeedbackEmail(author, feedback, attachment);
        } catch (MailException e) {
            // Ni le message ni l'email de l'auteur dans le journal : seulement de quoi diagnostiquer.
            log.warn("Feedback email delivery failed for user {}: {}", author.getId(), e.getClass().getSimpleName());
            throw new FeedbackDeliveryException(e);
        }
        rateLimiter.record(author.getId());
    }

    private static FeedbackRequest toFeedback(FeedbackForm form) {
        return new FeedbackRequest(
                parseType(form.type()),
                validMessage(form.message()),
                truncate(form.page()),
                truncate(form.userAgent()),
                truncate(form.viewport()),
                truncate(form.appVersion()));
    }

    private static FeedbackType parseType(String type) {
        if (type == null) {
            throw new BadRequestException(INVALID_TYPE_MESSAGE);
        }
        try {
            return FeedbackType.valueOf(type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_TYPE_MESSAGE);
        }
    }

    private static String validMessage(String message) {
        String stripped = message == null ? "" : message.strip();
        if (stripped.length() < MIN_MESSAGE_LENGTH || stripped.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException(INVALID_MESSAGE_LENGTH);
        }
        return stripped;
    }

    // Contexte fourni par le client à titre indicatif : borné pour ne pas gonfler l'email.
    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_CONTEXT_LENGTH ? value.substring(0, MAX_CONTEXT_LENGTH) : value;
    }

    private FeedbackScreenshot toAttachment(MultipartFile screenshot) {
        return screenshotValidator.validate(screenshot)
                .map(contentType -> new FeedbackScreenshot(bytesOf(screenshot), contentType))
                .orElse(null);
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la capture impossible", e);
        }
    }
}
