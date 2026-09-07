package com.adac.portail.repository;

import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Full history (TICKET-033: {@code GET /api/notifications}, no {@code ?read} filter) — most
     * recent first, capped at 200 (branch-wide review): notifications are never hard-deleted (only
     * dismissed from the bell), so this table grows without bound for the life of an account —
     * an uncapped query here is unbounded work and payload size on every call.
     */
    List<Notification> findTop200ByRecipientOrderByCreatedAtDesc(User recipient);

    /** {@code GET /api/notifications?read=true|false} — same cap, same reasoning. */
    List<Notification> findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(User recipient, boolean isRead);

    /**
     * The bell view (TICKET-033: {@code GET /api/notifications/unread}) — unread AND not
     * dismissed from the bell, capped at 50 (a dropdown listing more than that is already a
     * degenerate UI case). Replaces the TICKET-005 placeholder {@code
     * findAllByRecipientAndDeletedFromBellFalse}, which didn't filter by read status and so didn't
     * actually match "uniquement readAt IS NULL ET deletedFromBell = false" (this ticket's own
     * acceptance criterion).
     */
    List<Notification> findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(User recipient);

    /**
     * The bell badge count — deliberately a separate {@code count(*)} rather than {@code
     * findTop50(...).size()} (branch-wide review): the badge must show the true unread count even
     * when there are more than the 50 the dropdown lists, so it can't be derived from that capped
     * list. Cheap regardless of table size — matches {@code idx_notifications_recipient_is_read}.
     */
    long countByRecipientAndIsReadFalseAndDeletedFromBellFalse(User recipient);

    /**
     * Fetch + ownership check in one query (branch-wide review) — {@code
     * NotificationServiceImpl.findOwnedOrThrow} used to fetch by id, then compare {@code
     * recipient.getId()} in Java; pushing the predicate into the query makes fetching a foreign
     * row structurally impossible instead of relying on every future by-id method remembering the
     * check.
     */
    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    /**
     * {@code PATCH /api/notifications/read-all} (docs/tech.md § 8) — bulk update in one statement
     * rather than loading every unread row just to flip one field on each. {@code @Modifying}
     * queries always need an enclosing transaction (provided by the service method) and bypass the
     * persistence context entirely — both halves matter here: {@code clearAutomatically = true} so
     * an already-loaded {@code Notification} in the same transaction isn't served stale
     * (first-level-cache) data by a subsequent read after this runs, and {@code
     * flushAutomatically = true} so any pending changes on other entities in a wider transaction
     * are written before the clear, instead of being silently discarded by it (this service method
     * has nothing else pending today, but this is the codebase's first {@code @Modifying} query and
     * a future caller composing it into a larger transaction must not lose unrelated writes).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.recipient = :recipient and n.isRead = false")
    int markAllAsReadForRecipient(@Param("recipient") User recipient);
}
