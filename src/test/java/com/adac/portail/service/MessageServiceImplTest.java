package com.adac.portail.service;

import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.Message;
import com.adac.portail.entity.MessageRecipient;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.mapper.MessageMapper;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.repository.MessageRecipientRepository;
import com.adac.portail.repository.MessageRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TICKET-029 — see docs/tickets/TICKET-029.md § Write tests first. */
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageRecipientRepository messageRecipientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MessageServiceImpl messageService;

    private static User user(long id, Role role, boolean active) {
        return User.builder().id(id).nom("Doe").prenom("Jane").role(role).isActive(active).build();
    }

    private static AdacUserDetails principal(User user) {
        return new AdacUserDetails(user);
    }

    // --- sendMessage: role matrix (ticket ACs), exhaustive over the 3x3x2 combinations ----------

    @ParameterizedTest(name = "{0} -> {1} (target active={2}) allowed={3}")
    @CsvSource({
            "SUPER_ADMIN, SUPER_ADMIN, true,  true",
            "SUPER_ADMIN, ADMIN,       false, true", // AC: SUPER_ADMIN may write to *anyone*, even inactive
            "SUPER_ADMIN, STAGIAIRE,   false, true",
            "ADMIN,       SUPER_ADMIN, true,  true",
            "ADMIN,       SUPER_ADMIN, false, true", // no active check on a SUPER_ADMIN target — see canMessage's Javadoc
            "ADMIN,       ADMIN,       true,  true",
            "ADMIN,       ADMIN,       false, false",
            "ADMIN,       STAGIAIRE,   true,  true",
            "ADMIN,       STAGIAIRE,   false, false",
            "STAGIAIRE,   SUPER_ADMIN, true,  true",
            "STAGIAIRE,   ADMIN,       true,  true",
            "STAGIAIRE,   ADMIN,       false, false",
            "STAGIAIRE,   STAGIAIRE,   true,  false", // ticket Test 2: no stagiaire-to-stagiaire messaging
            "STAGIAIRE,   STAGIAIRE,   false, false",
    })
    void sendMessageEnforcesTheFullRoleMatrix(Role senderRole, Role targetRole, boolean targetActive, boolean allowed) {
        User sender = user(1L, senderRole, true);
        User target = user(2L, targetRole, targetActive);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        if (allowed) {
            stubSendSucceeds(sender, target);
            assertThat(messageService.sendMessage(principal(sender), new SendMessageRequest("Salut", List.of(2L), null)))
                    .isNotNull();
        } else {
            assertThatThrownBy(() -> messageService.sendMessage(principal(sender),
                    new SendMessageRequest("Salut", List.of(2L), null)))
                    .isInstanceOf(UnauthorizedException.class);
            verify(messageRepository, never()).save(any());
        }
    }

    // --- sendMessage: other behaviour ---------------------------------------------------------

    // Branch-wide review: unknown recipient and disallowed recipient must be indistinguishable
    // (both 403) — a 404 on "unknown" would let any authenticated caller map the user-id space.
    @Test
    void sendMessageWithUnknownRecipientThrowsUnauthorizedNotNotFound() {
        User sender = user(1L, Role.SUPER_ADMIN, true);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(principal(sender),
                new SendMessageRequest("Salut", List.of(404L), null)))
                .isInstanceOf(UnauthorizedException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessageWithNullRecipientIdsThrowsBadRequest() {
        User sender = user(1L, Role.SUPER_ADMIN, true);

        assertThatThrownBy(() -> messageService.sendMessage(principal(sender),
                new SendMessageRequest("Salut", null, null)))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void sendMessageWithMoreThanOneRecipientIdThrowsBadRequest() {
        User sender = user(1L, Role.SUPER_ADMIN, true);

        assertThatThrownBy(() -> messageService.sendMessage(principal(sender),
                new SendMessageRequest("Salut", List.of(2L, 3L), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sendMessagePersistsTheMessageWithTheRealSenderAndContent() {
        User sender = user(1L, Role.SUPER_ADMIN, true);
        User recipient = user(2L, Role.STAGIAIRE, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        stubSendSucceeds(sender, recipient);

        messageService.sendMessage(principal(sender), new SendMessageRequest("Salut toi", List.of(2L), null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isSameAs(sender);
        assertThat(captor.getValue().getContent()).isEqualTo("Salut toi");
        assertThat(captor.getValue().isGroup()).isFalse();
    }

    @Test
    void sendMessagePersistsTheRecipientRow() {
        User sender = user(1L, Role.SUPER_ADMIN, true);
        User recipient = user(2L, Role.STAGIAIRE, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        Message saved = stubSendSucceeds(sender, recipient);

        messageService.sendMessage(principal(sender), new SendMessageRequest("Salut", List.of(2L), null));

        ArgumentCaptor<MessageRecipient> captor = ArgumentCaptor.forClass(MessageRecipient.class);
        verify(messageRecipientRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isSameAs(saved);
        assertThat(captor.getValue().getRecipient()).isSameAs(recipient);
    }

    // Ticket Test 4: sendMessage triggers a notification for the recipient. Distinct ids for
    // sender (1), recipient (2) and the saved message (10) — a collision here would let this pass
    // whether the impl notifies with the sender's id or the message's id (branch-wide review).
    @Test
    void sendMessageNotifiesTheRecipient() {
        User sender = user(1L, Role.SUPER_ADMIN, true);
        User recipient = user(2L, Role.STAGIAIRE, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        Message saved = Message.builder().id(10L).sender(sender).content("Salut").createdAt(OffsetDateTime.now()).build();
        when(messageRepository.save(any())).thenReturn(saved);
        when(messageRecipientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageMapper.toResponse(saved)).thenReturn(MessageResponse.builder().id(10L).build());
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        messageService.sendMessage(principal(sender), new SendMessageRequest("Salut", List.of(2L), null));

        verify(notificationService).notify(eq(2L), eq(NotificationType.NEW_MESSAGE),
                eq("Nouveau message de Jane Doe"), eq(EntityType.MESSAGE), eq(1L));
    }

    @Test
    void sendMessageReturnsResponseWithRecipientAndGroupFalse() {
        User sender = user(1L, Role.SUPER_ADMIN, true);
        User recipient = user(2L, Role.STAGIAIRE, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        Message saved = Message.builder().id(10L).sender(sender).content("Salut").createdAt(OffsetDateTime.now()).build();
        when(messageRepository.save(any())).thenReturn(saved);
        MessageRecipient savedRow = MessageRecipient.builder().id(1L).message(saved).recipient(recipient).build();
        when(messageRecipientRepository.save(any())).thenReturn(savedRow);
        when(messageMapper.toResponse(saved)).thenReturn(
                MessageResponse.builder().id(10L).group(false).createdAt(saved.getCreatedAt()).build());
        when(userMapper.toResponse(recipient)).thenReturn(UserResponse.builder().id(2L).build());

        MessageResponse result = messageService.sendMessage(principal(sender),
                new SendMessageRequest("Salut", List.of(2L), null));

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.isGroup()).isFalse();
        assertThat(result.getReadAt()).isNull();
        assertThat(result.getRecipients()).extracting(UserResponse::getId).containsExactly(2L);
    }

    // --- markAsRead --------------------------------------------------------------------------

    // Ticket Test 5 (adapted, see revision note): marks the one message, not the whole thread.
    @Test
    void markAsReadSetsReadAtOnTheCallersRecipientRow() {
        User currentUser = user(2L, Role.STAGIAIRE, true);
        Message message = Message.builder().id(10L).build();
        MessageRecipient recipientRow = MessageRecipient.builder().id(100L).message(message).recipient(currentUser).build();
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(messageRecipientRepository.findByMessageAndRecipient(message, currentUser))
                .thenReturn(Optional.of(recipientRow));

        messageService.markAsRead(principal(currentUser), 10L);

        assertThat(recipientRow.getReadAt()).isNotNull();
    }

    @Test
    void markAsReadIsIdempotentAndDoesNotAdvanceAnAlreadyReadTimestamp() {
        User currentUser = user(2L, Role.STAGIAIRE, true);
        Message message = Message.builder().id(10L).build();
        OffsetDateTime originalReadAt = OffsetDateTime.now().minusHours(1);
        MessageRecipient recipientRow = MessageRecipient.builder().id(100L).message(message)
                .recipient(currentUser).readAt(originalReadAt).build();
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(messageRecipientRepository.findByMessageAndRecipient(message, currentUser))
                .thenReturn(Optional.of(recipientRow));

        messageService.markAsRead(principal(currentUser), 10L);

        assertThat(recipientRow.getReadAt()).isEqualTo(originalReadAt);
    }

    @Test
    void markAsReadWithUnknownMessageThrowsResourceNotFound() {
        User currentUser = user(2L, Role.STAGIAIRE, true);
        when(messageRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(principal(currentUser), 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAsReadWhenCallerIsNotARecipientThrowsResourceNotFound() {
        User currentUser = user(2L, Role.STAGIAIRE, true);
        Message message = Message.builder().id(10L).build();
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));
        when(messageRecipientRepository.findByMessageAndRecipient(message, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.markAsRead(principal(currentUser), 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getConversations ----------------------------------------------------------------------

    @Test
    void getConversationsWithNoPartnersReturnsEmptyListWithoutFurtherQueries() {
        User currentUser = user(1L, Role.SUPER_ADMIN, true);
        when(messageRepository.findConversationPartnerIds(1L)).thenReturn(List.of());

        assertThat(messageService.getConversations(principal(currentUser))).isEmpty();
        verify(messageRepository, never()).findLastMessageIdPerPartner(any(), any());
    }

    @Test
    void getConversationsPicksTheActualLatestMessagePerPartnerAndSortsDescending() {
        User currentUser = user(1L, Role.SUPER_ADMIN, true);
        User older = user(2L, Role.STAGIAIRE, true);
        User newer = user(3L, Role.STAGIAIRE, true);
        when(messageRepository.findConversationPartnerIds(1L)).thenReturn(List.of(2L, 3L));

        // The last-message-id query is the one thing that decides "latest" — pin it explicitly
        // rather than a single-message-per-thread fixture that can't distinguish "last" from
        // "only" (branch-wide review).
        MessageRepository.PartnerLastMessageId lastFor2 = partnerLastMessageId(2L, 100L);
        MessageRepository.PartnerLastMessageId lastFor3 = partnerLastMessageId(3L, 101L);
        when(messageRepository.findLastMessageIdPerPartner(1L, List.of(2L, 3L))).thenReturn(List.of(lastFor2, lastFor3));

        Message msgFor2 = Message.builder().id(100L).sender(currentUser).createdAt(OffsetDateTime.now().minusDays(1)).build();
        Message msgFor3 = Message.builder().id(101L).sender(currentUser).createdAt(OffsetDateTime.now()).build();
        when(messageRepository.findAllByIdWithSender(List.of(100L, 101L))).thenReturn(List.of(msgFor2, msgFor3));

        MessageRecipient rowFor2 = MessageRecipient.builder().message(msgFor2).recipient(older).build();
        MessageRecipient rowFor3 = MessageRecipient.builder().message(msgFor3).recipient(newer).build();
        when(messageRecipientRepository.findAllByMessageInAndRecipientIdIn(List.of(msgFor2, msgFor3), List.of(1L, 2L, 3L)))
                .thenReturn(List.of(rowFor2, rowFor3));

        when(messageRecipientRepository.countUnreadGroupedBySender(currentUser)).thenReturn(
                List.of(unreadCount(3L, 2L)));

        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(older, newer));
        when(messageMapper.toResponse(msgFor2)).thenReturn(MessageResponse.builder().createdAt(msgFor2.getCreatedAt()).build());
        when(messageMapper.toResponse(msgFor3)).thenReturn(MessageResponse.builder().createdAt(msgFor3.getCreatedAt()).build());
        when(userMapper.toResponse(older)).thenReturn(UserResponse.builder().id(2L).build());
        when(userMapper.toResponse(newer)).thenReturn(UserResponse.builder().id(3L).build());

        List<ConversationResponse> result = messageService.getConversations(principal(currentUser));

        assertThat(result).extracting(ConversationResponse::getConversationId).containsExactly(3L, 2L);
        assertThat(result.get(0).getUnreadCount()).isEqualTo(2);
        assertThat(result.get(1).getUnreadCount()).isEqualTo(0);
    }

    // --- getConversationMessages -----------------------------------------------------------------

    // Branch-wide review: an unknown conversationId must be indistinguishable from a known user
    // with no shared history — both return an empty thread, never a 404 (existence oracle).
    @Test
    void getConversationMessagesWithNoSharedHistoryReturnsEmptyListWithoutLookingUpTheUser() {
        User currentUser = user(1L, Role.SUPER_ADMIN, true);
        when(messageRepository.findConversationBetween(1L, 404L)).thenReturn(List.of());

        assertThat(messageService.getConversationMessages(principal(currentUser), 404L)).isEmpty();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getConversationMessagesResolvesReadAtPerMessageInOneBatchCall() {
        User currentUser = user(1L, Role.SUPER_ADMIN, true);
        User partner = user(2L, Role.STAGIAIRE, true);

        Message fromCurrentUser = Message.builder().id(10L).sender(currentUser).build();
        Message fromPartner = Message.builder().id(11L).sender(partner).build();
        when(messageRepository.findConversationBetween(1L, 2L)).thenReturn(List.of(fromCurrentUser, fromPartner));

        OffsetDateTime readAt = OffsetDateTime.now();
        MessageRecipient recipientRowForFirst = MessageRecipient.builder().message(fromCurrentUser).recipient(partner).readAt(null).build();
        MessageRecipient recipientRowForSecond = MessageRecipient.builder().message(fromPartner).recipient(currentUser).readAt(readAt).build();
        when(messageRecipientRepository.findAllByMessageInAndRecipientIdIn(
                List.of(fromCurrentUser, fromPartner), List.of(1L, 2L)))
                .thenReturn(List.of(recipientRowForFirst, recipientRowForSecond));

        when(messageMapper.toResponse(fromCurrentUser)).thenReturn(MessageResponse.builder().id(10L).build());
        when(messageMapper.toResponse(fromPartner)).thenReturn(MessageResponse.builder().id(11L).build());
        when(userMapper.toResponse(partner)).thenReturn(UserResponse.builder().id(2L).build());
        when(userMapper.toResponse(currentUser)).thenReturn(UserResponse.builder().id(1L).build());

        List<MessageResponse> result = messageService.getConversationMessages(principal(currentUser), 2L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReadAt()).isNull();
        assertThat(result.get(1).getReadAt()).isEqualTo(readAt);
        // Only one call, not one per message.
        verify(messageRecipientRepository, never()).findByMessageAndRecipient(any(), any());
    }

    // Defensive: a future group message (TICKET-030) puts two MessageRecipient rows on one
    // Message — the batch resolver must not crash the whole thread read over it (branch-wide
    // review: the original Collectors.toMap threw IllegalStateException on a duplicate key).
    @Test
    void getConversationMessagesToleratesMoreThanOneRecipientRowForTheSameMessage() {
        User currentUser = user(1L, Role.SUPER_ADMIN, true);
        Message groupMessage = Message.builder().id(10L).sender(currentUser).isGroup(true).build();
        when(messageRepository.findConversationBetween(1L, 2L)).thenReturn(List.of(groupMessage));

        MessageRecipient rowA = MessageRecipient.builder().message(groupMessage).recipient(user(2L, Role.STAGIAIRE, true)).build();
        MessageRecipient rowB = MessageRecipient.builder().message(groupMessage).recipient(user(3L, Role.STAGIAIRE, true)).build();
        when(messageRecipientRepository.findAllByMessageInAndRecipientIdIn(List.of(groupMessage), List.of(1L, 2L)))
                .thenReturn(List.of(rowA, rowB));
        when(messageMapper.toResponse(groupMessage)).thenReturn(MessageResponse.builder().id(10L).build());
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        List<MessageResponse> result = messageService.getConversationMessages(principal(currentUser), 2L);

        assertThat(result).hasSize(1);
    }

    private static MessageRepository.PartnerLastMessageId partnerLastMessageId(long partnerId, long messageId) {
        return new MessageRepository.PartnerLastMessageId() {
            @Override
            public Long getPartnerId() {
                return partnerId;
            }

            @Override
            public Long getMessageId() {
                return messageId;
            }
        };
    }

    private static MessageRecipientRepository.UnreadCountBySender unreadCount(long senderId, long count) {
        return new MessageRecipientRepository.UnreadCountBySender() {
            @Override
            public Long getSenderId() {
                return senderId;
            }

            @Override
            public Long getUnreadCount() {
                return count;
            }
        };
    }

    private Message stubSendSucceeds(User sender, User recipient) {
        Message saved = Message.builder().id(1L).sender(sender).content("Salut").createdAt(OffsetDateTime.now()).build();
        when(messageRepository.save(any())).thenReturn(saved);
        when(messageRecipientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageMapper.toResponse(saved)).thenReturn(MessageResponse.builder().id(1L).createdAt(saved.getCreatedAt()).build());
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());
        return saved;
    }
}
