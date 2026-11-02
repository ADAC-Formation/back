package com.adac.portail.repository;

import com.adac.portail.entity.Message;
import com.adac.portail.entity.MessageRecipient;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    Optional<MessageRecipient> findByMessageAndRecipient(Message message, User recipient);

    List<MessageRecipient> findAllByRecipient(User recipient);

    long countByRecipientAndReadAtIsNull(User recipient);

    long countByRecipientAndMessageSenderAndReadAtIsNull(User recipient, User sender);

    @Query("""
            SELECT mr.message.sender.id AS senderId, COUNT(mr) AS unreadCount
            FROM MessageRecipient mr
            WHERE mr.recipient = :recipient AND mr.readAt IS NULL
            GROUP BY mr.message.sender.id
            """)
    List<UnreadCountBySender> countUnreadGroupedBySender(@Param("recipient") User recipient);

    @Query("""
            SELECT mr FROM MessageRecipient mr JOIN FETCH mr.recipient
            WHERE mr.message IN :messages AND mr.recipient.id IN :recipientIds
            """)
    List<MessageRecipient> findAllByMessageInAndRecipientIdIn(
            @Param("messages") List<Message> messages, @Param("recipientIds") List<Long> recipientIds);

    interface UnreadCountBySender {
        Long getSenderId();

        Long getUnreadCount();
    }
}
