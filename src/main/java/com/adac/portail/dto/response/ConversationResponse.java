package com.adac.portail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fully synthetic — assembled by the service layer (TICKET-029), not mapped from a single
 * entity; there is no {@code Conversation} table (see docs/DB_MODEL.md — Décisions 3NF).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponse {

    /** = the other participant's userId, not a dedicated table's id (see docs/tech.md). */
    private Long conversationId;

    private UserResponse participant;
    private MessageResponse lastMessage;
    private int unreadCount;
}
