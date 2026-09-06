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

    /** Unread count across every conversation — for the notification bell badge. */
    long countByRecipientAndReadAtIsNull(User recipient);

    /** Unread count for one conversation thread — what ConversationResponse.unreadCount needs. */
    long countByRecipientAndMessageSenderAndReadAtIsNull(User recipient, User sender);

    /**
     * One unread count per sender for {@code recipient}, in a single query — what
     * {@code MessageServiceImpl.getConversations} needs instead of one
     * {@code countByRecipientAndMessageSenderAndReadAtIsNull} call per conversation partner.
     */
    @Query("""
            SELECT mr.message.sender.id AS senderId, COUNT(mr) AS unreadCount
            FROM MessageRecipient mr
            WHERE mr.recipient = :recipient AND mr.readAt IS NULL
            GROUP BY mr.message.sender.id
            """)
    List<UnreadCountBySender> countUnreadGroupedBySender(@Param("recipient") User recipient);

    /**
     * Batches the per-message recipient lookup a thread/last-message view needs (readAt,
     * recipient identity) into one query — see {@code MessageServiceImpl.resolveRecipientRows}.
     *
     * <p>Filtered to {@code recipientIds} (not just {@code messages}) so it stays correct once a
     * message can have more than one recipient (TICKET-030, group send): a caller only ever wants
     * the row(s) for the participants of the conversation being read, never an unrelated
     * recipient's — {@code JOIN FETCH mr.recipient} avoids an extra round trip per distinct
     * recipient once the caller reads {@code recipientRow.getRecipient()}.</p>
     */
    @Query("""
            SELECT mr FROM MessageRecipient mr JOIN FETCH mr.recipient
            WHERE mr.message IN :messages AND mr.recipient.id IN :recipientIds
            """)
    List<MessageRecipient> findAllByMessageInAndRecipientIdIn(
            @Param("messages") List<Message> messages, @Param("recipientIds") List<Long> recipientIds);

    /** Projection for {@link #countUnreadGroupedBySender}. */
    interface UnreadCountBySender {
        Long getSenderId();

        Long getUnreadCount();
    }
}
