package com.adac.portail.service;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * TICKET-029 laid the {@code notify} slice; TICKET-033 is the full CRUD (bell view, history,
 * mark-read, delete-from-bell) — see docs/tech.md § 8. Every read/write here is scoped to {@code
 * principal}'s own notifications; there is no cross-user access at all (not even for
 * SUPER_ADMIN — notifications are personal, not an admin resource).
 */
public interface NotificationService {

    /**
     * @param entityType nullable — the target's kind for click-to-navigate (e.g. {@code MESSAGE}),
     *                    or {@code null} if this notification has nothing to navigate to
     * @param entityId    nullable, same condition as {@code entityType}
     * @throws ResourceNotFoundException no user with {@code recipientId}
     */
    void notify(Long recipientId, NotificationType type, String content, EntityType entityType, Long entityId);

    /**
     * The full-page history — every notification of {@code principal}, most recent first.
     *
     * @param read {@code null} for all; otherwise filters to read ({@code true}) or unread
     *             ({@code false}) only (docs/tech.md: {@code ?read=true|false})
     */
    List<NotificationResponse> getNotifications(AdacUserDetails principal, Boolean read);

    /** The bell dropdown's payload — unread and not dismissed from the bell, most recent first. */
    UnreadNotificationsResponse getUnread(AdacUserDetails principal);

    /**
     * Idempotent — re-marking an already-read notification doesn't change anything.
     *
     * @throws ResourceNotFoundException no notification with this id, or it isn't {@code
     *                                    principal}'s (same status either way — a 403 would
     *                                    confirm it exists and just isn't theirs, the same
     *                                    reasoning as {@code MessageService.markAsRead})
     */
    NotificationResponse markAsRead(AdacUserDetails principal, Long notificationId);

    /** {@code PATCH /api/notifications/read-all} — marks every one of {@code principal}'s unread notifications as read. */
    void markAllAsRead(AdacUserDetails principal);

    /**
     * Hides the notification from the bell ({@code deletedFromBell = true}) without touching the
     * full history. Idempotent, same not-found reasoning as {@link #markAsRead}.
     */
    void deleteFromBell(AdacUserDetails principal, Long notificationId);
}
