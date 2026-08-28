package com.adac.portail.repository;

import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Named after the entity's actual field ({@code recipient}, not {@code user}). */
    List<Notification> findAllByRecipient(User recipient);

    List<Notification> findAllByRecipientAndDeletedFromBellFalse(User recipient);
}
