package com.adac.portail.service;

import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.exception.ResourceNotFoundException;

/**
 * TICKET-029 — minimal slice of the eventual TICKET-033 service: only {@code notify}, the one
 * method {@link MessageService} needs to satisfy "sending a message notifies its recipient". The
 * full CRUD (bell view, history, mark-read, delete-from-bell) lands with TICKET-033, extending
 * this interface rather than replacing it — same pattern as {@code ActivationServiceImpl} sending
 * mail directly ahead of a future {@code EmailService} (see docs/ARCHI.md).
 */
public interface NotificationService {

    /**
     * @param entityType nullable — the target's kind for click-to-navigate (e.g. {@code MESSAGE}),
     *                    or {@code null} if this notification has nothing to navigate to
     * @param entityId    nullable, same condition as {@code entityType}
     * @throws ResourceNotFoundException no user with {@code recipientId}
     */
    void notify(Long recipientId, NotificationType type, String content, EntityType entityType, Long entityId);
}
