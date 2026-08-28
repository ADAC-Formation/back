package com.adac.portail.repository;

import com.adac.portail.entity.Message;
import com.adac.portail.entity.MessageRecipient;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    Optional<MessageRecipient> findByMessageAndRecipient(Message message, User recipient);

    List<MessageRecipient> findAllByRecipient(User recipient);

    /** Unread count across every conversation — for the notification bell badge. */
    long countByRecipientAndReadAtIsNull(User recipient);

    /** Unread count for one conversation thread — what ConversationResponse.unreadCount needs. */
    long countByRecipientAndMessageSenderAndReadAtIsNull(User recipient, User sender);
}
