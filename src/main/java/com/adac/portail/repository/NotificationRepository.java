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

    List<Notification> findTop200ByRecipientOrderByCreatedAtDesc(User recipient);

    List<Notification> findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(User recipient, boolean isRead);

    List<Notification> findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(User recipient);

    long countByRecipientAndIsReadFalseAndDeletedFromBellFalse(User recipient);

    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.recipient = :recipient and n.isRead = false")
    int markAllAsReadForRecipient(@Param("recipient") User recipient);
}
