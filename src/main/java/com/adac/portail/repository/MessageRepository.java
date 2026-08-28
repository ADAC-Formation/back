package com.adac.portail.repository;

import com.adac.portail.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * All messages exchanged between two users, in either direction — a "conversation" has no
     * dedicated table (see docs/DB_MODEL.md — Décisions 3NF), it's computed by filtering on the
     * sender/recipient pair. Ordered oldest first, matching GET /api/messages/{conversationId}.
     * Plain {@code JOIN} (not {@code LEFT JOIN}): the WHERE clause already requires a matching
     * {@code mr} row, so a left join here was a no-op that just obscured intent. {@code JOIN
     * FETCH m.sender} avoids an N+1 when the caller maps each message's sender.
     */
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
}
