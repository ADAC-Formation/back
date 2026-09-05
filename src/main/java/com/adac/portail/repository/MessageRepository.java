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

    /**
     * Every distinct user {@code userId} has exchanged at least one message with, in either
     * direction — the set of "conversation partners" {@code GET /api/messages} lists one row per
     * (docs/tech.md). {@code DISTINCT} matters: a multi-recipient send (TICKET-030) would
     * otherwise still only ever contribute one partner id per {@code mr} row here anyway (this
     * counts partners, not messages), but two separate messages with the same partner must still
     * collapse to one row.
     */
    @Query("""
            SELECT DISTINCT CASE WHEN m.sender.id = :userId THEN mr.recipient.id ELSE m.sender.id END
            FROM Message m JOIN MessageRecipient mr ON mr.message = m
            WHERE m.sender.id = :userId OR mr.recipient.id = :userId
            """)
    List<Long> findConversationPartnerIds(@Param("userId") Long userId);

    /**
     * The most recent message id per partner in {@code partnerIds}, in one query — what
     * {@code GET /api/messages} needs instead of loading and discarding every message of every
     * thread (branch-wide review: the original per-partner loop was an N+1 that also pulled whole
     * conversation histories into memory just to read the last row). {@code MAX(m.id)}, not
     * {@code MAX(m.created_at)}: {@code id} is {@code IDENTITY}-generated and therefore already
     * monotonic with insertion order, and comparing it avoids a tie-break entirely (two messages
     * can share a {@code created_at} truncated to the column's precision).
     *
     * <p>Native SQL, not JPQL, and {@code GROUP BY 1} (positional) rather than repeating the
     * {@code CASE} expression: {@code :userId} is bound to a separate JDBC placeholder at each of
     * its three occurrences, and PostgreSQL requires a {@code GROUP BY} expression to be
     * *syntactically* identical to the one in {@code SELECT} to prove functional dependency —
     * different placeholder instances (even carrying the same value) don't qualify, so JPQL's
     * "repeat the CASE in GROUP BY" form fails at the database with "column must appear in the
     * GROUP BY clause". Grouping by ordinal position sidesteps the comparison entirely.</p>
     */
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

    /** {@code findConversationBetween}'s ordering doesn't matter here — just needs {@code sender} eagerly. */
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.id IN :ids")
    List<Message> findAllByIdWithSender(@Param("ids") List<Long> ids);

    /** Projection for {@link #findLastMessageIdPerPartner}. */
    interface PartnerLastMessageId {
        Long getPartnerId();

        Long getMessageId();
    }
}
