package com.adac.portail.service;

import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * Individual messaging (US-013) — see docs/tech.md § 7. Group messaging (a {@code filter} instead
 * of {@code recipientIds}, TICKET-030) is out of scope here; {@link #sendMessage} rejects it for
 * now rather than silently doing nothing.
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
     * @throws BadRequestException    {@code request.getRecipientIds()} doesn't have exactly one
     *                                 entry (group send via {@code request.getFilter()} is
     *                                 TICKET-030, not yet supported)
     * @throws ResourceNotFoundException the recipient doesn't exist
     * @throws UnauthorizedException  the sender's role isn't allowed to message this recipient
     *                                 (see docs/tech.md / docs/tickets/TICKET-029.md for the matrix)
     */
    MessageResponse sendMessage(AdacUserDetails principal, SendMessageRequest request);

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
