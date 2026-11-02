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

public interface MessageService {

    List<ConversationResponse> getConversations(AdacUserDetails principal);

    List<MessageResponse> getConversationMessages(AdacUserDetails principal, Long conversationId);

    MessageResponse sendMessage(AdacUserDetails principal, SendMessageRequest request);

    List<UserResponse> previewGroupRecipients(AdacUserDetails principal, SendMessageRequest.Filter filter);

    void markAsRead(AdacUserDetails principal, Long messageId);
}
