package com.adac.portail.service;

import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * Individual messaging (US-013) and group messaging (US-014, TICKET-030) — see docs/tech.md § 7.
 * Group send reuses {@link #sendMessage} with {@code request.getFilter()} set instead of a
 * dedicated endpoint (see docs/tech.md, not the ticket's own separate-endpoint sketch — TICKET-005
 * had already pre-built {@code SendMessageRequest.Filter}/{@code MessageFilterType} for exactly
 * this shape).
 */
public interface MessageService {

    /** One row per distinct correspondent, sorted by {@code lastMessage.createdAt} descending. */
    List<ConversationResponse> getConversations(AdacUserDetails principal);

    /**
     * @param conversationId the other participant's user id (not a dedicated entity — see
     *                       docs/tech.md), sorted oldest first
     * @throws ResourceNotFoundException no user with this id
     */
    List<MessageResponse> getConversationMessages(AdacUserDetails principal, Long conversationId);

    /**
     * Exactly one of {@code request.getRecipientIds()} (individual) / {@code request.getFilter()}
     * (group) must be set.
     *
     * @throws BadRequestException    neither or both of recipientIds/filter set; recipientIds
     *                                 doesn't have exactly one entry; {@code filter.formationId}
     *                                 missing for {@code FORMATION} or {@code filter.userIds}
     *                                 empty for {@code MANUAL}; or the filter resolved zero
     *                                 recipients
     * @throws ResourceNotFoundException the recipient (individual), the formation ({@code
     *                                 FORMATION}), or one of {@code filter.userIds} ({@code
     *                                 MANUAL}) doesn't exist
     * @throws UnauthorizedException  the sender's role isn't allowed to message this recipient
     *                                 (individual, see docs/tickets/TICKET-029.md for the matrix),
     *                                 or isn't allowed to use this filter type / formation ({@code
     *                                 FORMATION}: SUPER_ADMIN any, ADMIN their own only; {@code
     *                                 MISSING_DOCS}/{@code MANUAL}: SUPER_ADMIN only)
     */
    MessageResponse sendMessage(AdacUserDetails principal, SendMessageRequest request);

    /**
     * Resolves {@code filter}'s recipients without sending anything — same role/target rules as
     * the group branch of {@link #sendMessage}.
     */
    List<UserResponse> previewGroupRecipients(AdacUserDetails principal, SendMessageRequest.Filter filter);

    /**
     * Marks the single message {@code messageId} as read for the caller — not the whole
     * conversation (see docs/tickets/TICKET-029.md's revision note).
     *
     * @throws ResourceNotFoundException no message with this id, or the caller isn't one of its
     *                                    recipients (same status either way — a 403 would confirm
     *                                    the message exists)
     */
    void markAsRead(AdacUserDetails principal, Long messageId);
}
