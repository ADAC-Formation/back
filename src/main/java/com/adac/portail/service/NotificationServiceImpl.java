package com.adac.portail.service;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.NotificationMapper;
import com.adac.portail.repository.NotificationRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final int CONTENT_MAX_LENGTH = 255;
    private static final String NOT_FOUND_MESSAGE = "Notification introuvable";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

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

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(AdacUserDetails principal, Boolean read) {
        User caller = principal.getUser();
        List<Notification> notifications = read == null
                ? notificationRepository.findTop200ByRecipientOrderByCreatedAtDesc(caller)
                : notificationRepository.findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(caller, read);
        return notifications.stream().map(notificationMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationsResponse getUnread(AdacUserDetails principal) {
        User caller = principal.getUser();
        List<NotificationResponse> notifications = notificationRepository
                .findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(caller).stream()
                .map(notificationMapper::toResponse)
                .toList();

        long count = notificationRepository.countByRecipientAndIsReadFalseAndDeletedFromBellFalse(caller);
        return UnreadNotificationsResponse.builder()
                .count((int) count)
                .notifications(notifications)
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(AdacUserDetails principal, Long notificationId) {
        Notification notification = findOwnedOrThrow(principal, notificationId);

        notification.setRead(true);
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(AdacUserDetails principal) {
        notificationRepository.markAllAsReadForRecipient(principal.getUser());
    }

    @Override
    @Transactional
    public void deleteFromBell(AdacUserDetails principal, Long notificationId) {
        Notification notification = findOwnedOrThrow(principal, notificationId);
        notification.setDeletedFromBell(true);
    }

    private Notification findOwnedOrThrow(AdacUserDetails principal, Long notificationId) {
        return notificationRepository.findByIdAndRecipient(notificationId, principal.getUser())
                .orElseThrow(() -> {
                    log.warn("Notification {} not found or not owned by user {}",
                            notificationId, principal.getUser().getId());
                    return new ResourceNotFoundException(NOT_FOUND_MESSAGE);
                });
    }

    private String truncate(String content) {
        return content.length() > CONTENT_MAX_LENGTH ? content.substring(0, CONTENT_MAX_LENGTH) : content;
    }
}
