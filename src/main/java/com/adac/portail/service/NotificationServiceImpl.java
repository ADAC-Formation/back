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

/** See {@link NotificationService} for scope. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** Matches {@code notifications.content VARCHAR(255)} (V1__init_schema.sql). */
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
        // Separate count, not notifications.size() (branch-wide review): the badge must reflect
        // the true unread total even when it exceeds the 50-item cap above.
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
        // Idempotent: re-marking an already-read notification is a no-op, not an error.
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

    /**
     * Same not-found-for-both reasoning as {@code MessageServiceImpl.markAsRead}: a notification
     * that exists but belongs to someone else is indistinguishable from an unknown id — a 403
     * would itself confirm the id refers to a real notification. The ownership check is the
     * predicate itself ({@code findByIdAndRecipient}, branch-wide review), not a fetch followed by
     * a manual comparison — logged at WARN before throwing, since this is the feature's only
     * authorization boundary and a foreign-id probe must leave a trace even though the response
     * body/status stay identical to a genuine miss.
     */
    private Notification findOwnedOrThrow(AdacUserDetails principal, Long notificationId) {
        return notificationRepository.findByIdAndRecipient(notificationId, principal.getUser())
                .orElseThrow(() -> {
                    log.warn("Notification {} not found or not owned by user {}",
                            notificationId, principal.getUser().getId());
                    return new ResourceNotFoundException(NOT_FOUND_MESSAGE);
                });
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
