package com.adac.portail.service;

import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.repository.NotificationRepository;
import com.adac.portail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** See {@link NotificationService} for scope. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** Matches {@code notifications.content VARCHAR(255)} (V1__init_schema.sql). */
    private static final int CONTENT_MAX_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void notify(Long recipientId, NotificationType type, String content, EntityType entityType, Long entityId) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .content(truncate(content))
                .entityType(entityType)
                .entityId(entityId)
                .build());
    }

    /**
     * A caller-built {@code content} string (e.g. {@code MessageServiceImpl}'s "Nouveau message de
     * {prenom} {nom}", each up to 255 chars per {@code CreateUserRequest}) can exceed the column
     * width — without this, that insert throws a {@code DataIntegrityViolationException} the
     * caller has no reason to expect from a plain notification write (branch-wide review).
     */
    private String truncate(String content) {
        return content.length() > CONTENT_MAX_LENGTH ? content.substring(0, CONTENT_MAX_LENGTH) : content;
    }
}
