package com.adac.portail.service;

import com.adac.portail.dto.request.MessageFilterType;
import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.Formation;
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
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.MessageRecipientRepository;
import com.adac.portail.repository.MessageRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private static final String UNAUTHORIZED_MESSAGE = "Vous ne pouvez pas écrire à ce destinataire";
    private static final String NOT_FOUND_MESSAGE = "Message introuvable";
    private static final String SUPER_ADMIN_ONLY = "Ce filtre est réservé au Super Admin";
    private static final String GROUP_SEND_RESTRICTED = "L'envoi groupé est réservé au Super Admin et aux formateurs";
    private static final String NO_RECIPIENTS_RESOLVED = "Aucun destinataire trouvé pour ce filtre";

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final FormationService formationService;
    private final InscriptionRepository inscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(AdacUserDetails principal) {
        User currentUser = principal.getUser();
        List<Long> partnerIds = messageRepository.findConversationPartnerIds(currentUser.getId());
        if (partnerIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> lastMessageIdByPartnerId = messageRepository
                .findLastMessageIdPerPartner(currentUser.getId(), partnerIds).stream()
                .collect(Collectors.toMap(
                        MessageRepository.PartnerLastMessageId::getPartnerId,
                        MessageRepository.PartnerLastMessageId::getMessageId));
        List<Message> lastMessages = messageRepository.findAllByIdWithSender(
                List.copyOf(lastMessageIdByPartnerId.values()));
        Map<Long, Message> lastMessageById = lastMessages.stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        List<Long> relevantRecipientIds = concat(currentUser.getId(), partnerIds);
        Map<Long, MessageRecipient> recipientRowByMessageId = resolveRecipientRows(lastMessages, relevantRecipientIds);

        Map<Long, Long> unreadCountBySenderId = messageRecipientRepository.countUnreadGroupedBySender(currentUser)
                .stream()
                .collect(Collectors.toMap(
                        MessageRecipientRepository.UnreadCountBySender::getSenderId,
                        MessageRecipientRepository.UnreadCountBySender::getUnreadCount));

        Map<Long, User> partnerById = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return lastMessageIdByPartnerId.entrySet().stream()
                .map(entry -> {
                    Long partnerId = entry.getKey();
                    Message lastMessage = lastMessageById.get(entry.getValue());
                    return ConversationResponse.builder()
                            .conversationId(partnerId)
                            .participant(userMapper.toResponse(partnerById.get(partnerId)))
                            .lastMessage(toMessageResponse(lastMessage, recipientRowByMessageId.get(lastMessage.getId())))
                            .unreadCount(unreadCountBySenderId.getOrDefault(partnerId, 0L).intValue())
                            .build();
                })
                .sorted(Comparator.comparing((ConversationResponse c) -> c.getLastMessage().getCreatedAt()).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationMessages(AdacUserDetails principal, Long conversationId) {
        Long currentUserId = principal.getUser().getId();
        List<Message> thread = messageRepository.findConversationBetween(currentUserId, conversationId);
        if (thread.isEmpty()) {

            return List.of();
        }

        Map<Long, MessageRecipient> recipientRowByMessageId = resolveRecipientRows(
                thread, List.of(currentUserId, conversationId));

        return thread.stream()
                .map(message -> toMessageResponse(message, recipientRowByMessageId.get(message.getId())))
                .toList();
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(AdacUserDetails principal, SendMessageRequest request) {
        User sender = principal.getUser();
        boolean hasRecipientIds = request.getRecipientIds() != null;
        boolean hasFilter = request.getFilter() != null;
        if (hasRecipientIds == hasFilter) {
            throw new BadRequestException(
                    "Fournir exactement un des deux : recipientIds (individuel) ou filter (groupé)");
        }

        return hasFilter
                ? sendGroupMessage(principal, request.getContent(), request.getFilter())
                : sendIndividualMessage(sender, request.getContent(), request.getRecipientIds());
    }

    private MessageResponse sendIndividualMessage(User sender, String content, List<Long> recipientIds) {

        if (recipientIds.size() != 1) {
            throw new BadRequestException(
                    "recipientIds doit contenir exactement un destinataire pour un envoi individuel");
        }

        User recipient = userRepository.findById(recipientIds.get(0))
                .filter(target -> canMessage(sender.getRole(), target))
                .orElseThrow(() -> new UnauthorizedException(UNAUTHORIZED_MESSAGE));

        Message message = messageRepository.save(Message.builder()
                .sender(sender)
                .content(content)
                .isGroup(false)
                .build());
        MessageRecipient recipientRow = messageRecipientRepository.save(MessageRecipient.builder()
                .message(message)
                .recipient(recipient)
                .build());
        notifyAfterCommit(recipient.getId(), NotificationType.NEW_MESSAGE,
                "Nouveau message de " + sender.getPrenom() + " " + sender.getNom(),
                EntityType.MESSAGE, sender.getId());

        return toMessageResponse(message, recipientRow);
    }

    private MessageResponse sendGroupMessage(AdacUserDetails principal, String content, SendMessageRequest.Filter filter) {
        User sender = principal.getUser();
        List<User> recipients = resolveGroupRecipients(principal, filter);
        if (recipients.isEmpty()) {
            throw new BadRequestException(NO_RECIPIENTS_RESOLVED);
        }

        Message message = messageRepository.save(Message.builder()
                .sender(sender)
                .content(content)
                .isGroup(true)
                .build());

        List<UserResponse> recipientResponses = new ArrayList<>();
        for (User recipient : recipients) {
            messageRecipientRepository.save(MessageRecipient.builder()
                    .message(message)
                    .recipient(recipient)
                    .build());
            notifyAfterCommit(recipient.getId(), NotificationType.NEW_MESSAGE,
                    "Nouveau message de " + sender.getPrenom() + " " + sender.getNom(),
                    EntityType.MESSAGE, sender.getId());
            recipientResponses.add(userMapper.toResponse(recipient));
        }

        MessageResponse response = messageMapper.toResponse(message);
        response.setRecipients(recipientResponses);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> previewGroupRecipients(AdacUserDetails principal, SendMessageRequest.Filter filter) {
        return resolveGroupRecipients(principal, filter).stream().map(userMapper::toResponse).toList();
    }

    private List<User> resolveGroupRecipients(AdacUserDetails principal, SendMessageRequest.Filter filter) {
        User caller = principal.getUser();
        List<User> resolved = switch (filter.getType()) {
            case FORMATION -> resolveFormationRecipients(principal, filter.getFormationId());
            case MISSING_DOCS -> {
                requireSuperAdmin(caller);
                yield inscriptionRepository.findStagiairesWithNoDocuments();
            }
            case MANUAL -> {
                requireSuperAdmin(caller);
                yield resolveManualRecipients(filter.getUserIds());
            }
        };
        return resolved.stream().filter(target -> canMessage(caller.getRole(), target)).toList();
    }

    private List<User> resolveFormationRecipients(AdacUserDetails principal, Long formationId) {
        if (formationId == null) {
            throw new BadRequestException("formationId est requis pour le filtre FORMATION");
        }
        if (principal.getUser().getRole() == Role.STAGIAIRE) {
            throw new UnauthorizedException(GROUP_SEND_RESTRICTED);
        }
        Formation formation = formationService.findVisibleFormationOrThrow(formationId, principal);
        return inscriptionRepository.findStagiairesByFormation(formation);
    }

    private List<User> resolveManualRecipients(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BadRequestException("userIds est requis et non vide pour le filtre MANUAL");
        }

        List<Long> distinctIds = userIds.stream().distinct().toList();
        List<User> found = userRepository.findAllById(distinctIds);
        if (found.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("Un ou plusieurs destinataires sont introuvables");
        }
        return found;
    }

    private void requireSuperAdmin(User caller) {
        if (caller.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedException(SUPER_ADMIN_ONLY);
        }
    }

    @Override
    @Transactional
    public void markAsRead(AdacUserDetails principal, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        MessageRecipient recipientRow = messageRecipientRepository
                .findByMessageAndRecipient(message, principal.getUser())

                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));

        if (recipientRow.getReadAt() == null) {
            recipientRow.setReadAt(OffsetDateTime.now());
        }
    }

    private boolean canMessage(Role senderRole, User target) {
        return switch (senderRole) {
            case SUPER_ADMIN -> true;
            case ADMIN -> target.getRole() == Role.SUPER_ADMIN
                    || ((target.getRole() == Role.ADMIN || target.getRole() == Role.STAGIAIRE) && target.isActive());
            case STAGIAIRE -> target.getRole() == Role.SUPER_ADMIN
                    || (target.getRole() == Role.ADMIN && target.isActive());
        };
    }

    private void notifyAfterCommit(Long recipientId, NotificationType type, String content,
            EntityType entityType, Long entityId) {
        Runnable send = () -> {
            try {
                notificationService.notify(recipientId, type, content, entityType, entityId);
            } catch (RuntimeException e) {

                log.warn("Failed to create notification for message send", e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    private Map<Long, MessageRecipient> resolveRecipientRows(List<Message> messages, List<Long> candidateRecipientIds) {
        return messageRecipientRepository.findAllByMessageInAndRecipientIdIn(messages, candidateRecipientIds)
                .stream()
                .collect(Collectors.toMap(mr -> mr.getMessage().getId(), Function.identity(), (first, second) -> first));
    }

    private MessageResponse toMessageResponse(Message message, MessageRecipient recipientRow) {
        MessageResponse response = messageMapper.toResponse(message);
        if (recipientRow != null) {
            response.setRecipients(List.of(userMapper.toResponse(recipientRow.getRecipient())));
            response.setReadAt(recipientRow.getReadAt());
        }
        return response;
    }

    private static List<Long> concat(Long id, List<Long> ids) {
        return Stream.concat(Stream.of(id), ids.stream()).toList();
    }
}
