package com.adac.portail.repository;

import com.adac.portail.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            SELECT DISTINCT m FROM Message m
            JOIN FETCH m.sender
            JOIN MessageRecipient mr ON mr.message = m
            WHERE (m.sender.id = :userId AND mr.recipient.id = :otherUserId)
               OR (m.sender.id = :otherUserId AND mr.recipient.id = :userId)
            ORDER BY m.createdAt ASC
            """)
    List<Message> findConversationBetween(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    List<Message> findAllBySenderId(Long senderId);

    @Query("""
            SELECT DISTINCT CASE WHEN m.sender.id = :userId THEN mr.recipient.id ELSE m.sender.id END
            FROM Message m JOIN MessageRecipient mr ON mr.message = m
            WHERE m.sender.id = :userId OR mr.recipient.id = :userId
            """)
    List<Long> findConversationPartnerIds(@Param("userId") Long userId);

    @Query(nativeQuery = true, value = """
            SELECT CASE WHEN m.sender_id = :userId THEN mr.recipient_id ELSE m.sender_id END AS partnerId,
                   MAX(m.id) AS messageId
            FROM messages m JOIN message_recipients mr ON mr.message_id = m.id
            WHERE (m.sender_id = :userId AND mr.recipient_id IN :partnerIds)
               OR (mr.recipient_id = :userId AND m.sender_id IN :partnerIds)
            GROUP BY 1
            """)
    List<PartnerLastMessageId> findLastMessageIdPerPartner(
            @Param("userId") Long userId, @Param("partnerIds") List<Long> partnerIds);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.id IN :ids")
    List<Message> findAllByIdWithSender(@Param("ids") List<Long> ids);

    interface PartnerLastMessageId {
        Long getPartnerId();

        Long getMessageId();
    }
}
