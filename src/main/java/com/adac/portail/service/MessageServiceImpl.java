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

/**
 * See {@link MessageService} for the contract; docs/tech.md § 7 for the wire shapes.
 *
 * <p>Group send (TICKET-030, {@code request.getFilter()} set instead of {@code recipientIds})
 * shares the same {@code Message} + N {@code MessageRecipient} rows model an individual send
 * already uses for its one row — {@link MessageRepository}'s batch reads were written defensively
 * from the start specifically to already tolerate this (see their own Javadoc), so no read-side
 * change was needed to support it.</p>
 */
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

        // Six queries total, independent of how many correspondents there are — see
        // MessageRepository.findLastMessageIdPerPartner's Javadoc for why the original per-partner
        // loop was a problem (branch-wide review).
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
            // Same response whether conversationId names a real user with no shared history, or
            // no user at all — a 404 for the latter would let any authenticated caller enumerate
            // the user-id space (branch-wide review; see the equivalent choice in markAsRead).
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
        // Exactly one, not "at least one": tech.md's own individual-send example is a
        // single-element list — group sends now go through sendGroupMessage instead (TICKET-030).
        if (recipientIds.size() != 1) {
            throw new BadRequestException(
                    "recipientIds doit contenir exactement un destinataire pour un envoi individuel");
        }

        // Unknown id and disallowed-target id get the identical 403: distinguishing them (the
        // original shape, 404 vs 403) would let any authenticated caller enumerate which user ids
        // exist by watching which status comes back (branch-wide review — same class of oracle
        // closed for login status in commit 0acb86b).
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

    /**
     * TICKET-030 — one {@code Message} row, one {@code MessageRecipient} row (and one
     * notification) per resolved recipient. Reuses {@link #resolveGroupRecipients}, the same
     * resolution {@link #previewGroupRecipients} uses, so "who would receive this" and "who
     * actually did" can never drift apart. Plain {@code for} loop, not {@code stream().map(...)}:
     * each iteration does two side effects (persist the recipient row, register a notification)
     * before producing a value — a stream pretends that's a pure transformation (branch-wide
     * review).
     */
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

    /**
     * docs/tech.md § 7 filter types, role rules per docs/tickets/TICKET-030.md's acceptance
     * criteria: {@code FORMATION} — SUPER_ADMIN any formation, ADMIN their own only, STAGIAIRE
     * never; {@code MISSING_DOCS}/{@code MANUAL} — SUPER_ADMIN only.
     *
     * <p>Branch-wide review (BLOCKING): the resolved list is re-filtered through {@link
     * #canMessage} before being returned — without this, a filter was a way to reach (group send)
     * or discover (preview, via {@code UserResponse}) a target the identical individual send would
     * reject with 403 (e.g. an ADMIN's own deactivated stagiaire). {@code canMessage(SUPER_ADMIN,
     * _)} is always {@code true}, so this is a no-op for that role.</p>
     */
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

    /**
     * Branch-wide review (BLOCKING): STAGIAIRE is rejected <em>before</em> the formation lookup —
     * they may never use this filter regardless of {@code formationId}, so no lookup should run
     * for them at all (matches {@code MISSING_DOCS}/{@code MANUAL}'s {@code requireSuperAdmin}
     * running first, and avoids a second id-enumeration surface for a role that's blocked
     * unconditionally). ADMIN/SUPER_ADMIN then delegate to {@link
     * FormationService#findVisibleFormationOrThrow} — the same 404-for-non-owning-ADMIN rule
     * {@code InscriptionServiceImpl} already reuses, instead of a second, divergent copy of it
     * (the previous version of this method threw 403 here, reopening the id-enumeration oracle
     * that method's own Javadoc documents closing).
     */
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
        // distinct() before the lookup (branch-wide review): findAllById collapses a duplicate id
        // to one row, so comparing its size against the raw, non-deduped userIds would reject a
        // fully valid request (e.g. ?userIds=4&userIds=4) as "destinataire introuvable".
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
                // Same status as an unknown message id: a 403 here would confirm the message
                // exists and just isn't addressed to this caller.
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        // Idempotent: re-marking an already-read message must not push readAt forward, or a
        // "when did they actually read this" read receipt drifts on every re-render/retry.
        if (recipientRow.getReadAt() == null) {
            recipientRow.setReadAt(OffsetDateTime.now());
        }
    }

    /**
     * See docs/tickets/TICKET-029.md's acceptance criteria for the matrix: SUPER_ADMIN → anyone;
     * ADMIN → SUPER_ADMIN, any active ADMIN, any active STAGIAIRE; STAGIAIRE → SUPER_ADMIN, any
     * active ADMIN only (no stagiaire-to-stagiaire messaging). The SUPER_ADMIN branch has no
     * {@code isActive()} check, unlike every other branch — deliberate, not an oversight: there is
     * exactly one such account at this project's scale, and requiring it active would mean "the
     * org's one Super Admin deactivates themselves" also cuts everyone else off from ever
     * reaching them again. Revisit if that assumption stops holding (branch-wide review).
     */
    private boolean canMessage(Role senderRole, User target) {
        return switch (senderRole) {
            case SUPER_ADMIN -> true;
            case ADMIN -> target.getRole() == Role.SUPER_ADMIN
                    || ((target.getRole() == Role.ADMIN || target.getRole() == Role.STAGIAIRE) && target.isActive());
            case STAGIAIRE -> target.getRole() == Role.SUPER_ADMIN
                    || (target.getRole() == Role.ADMIN && target.isActive());
        };
    }

    /**
     * Defers the notification write until the enclosing transaction commits, and never lets it
     * fail the send. Two independent reasons (branch-wide review): (1) {@code notify} does its own
     * database write — a constraint failure there (e.g. content overflow) must not roll back a
     * message that has otherwise fully succeeded; (2) calling it inline would create the
     * notification before the message itself is durable, so a rollback after this point (a
     * constraint violation on the message/recipient insert) would leave a notification pointing at
     * a message that never existed. Mirrors {@code UserServiceImpl.sendActivationCodeAfterCommit}.
     */
    private void notifyAfterCommit(Long recipientId, NotificationType type, String content,
            EntityType entityType, Long entityId) {
        Runnable send = () -> {
            try {
                notificationService.notify(recipientId, type, content, entityType, entityId);
            } catch (RuntimeException e) {
                // A missing/failed notification is a degraded experience, not a reason to have
                // rejected (or, post-commit, to crash) an otherwise-successful send.
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

    /**
     * One batch query resolving each of {@code messages}' recipient row, restricted to
     * {@code candidateRecipientIds} — the participants the caller is actually allowed to see. Safe
     * even if a message in {@code messages} has recipients outside that set (a future group
     * message, TICKET-030): those rows are filtered out by the query itself rather than
     * accidentally leaking into the result, and {@code toMap} with a "first wins" merge means a
     * message still can't crash this even if two recipient rows both matched, e.g. two different
     * accounts of the current user (defensive; not currently possible).
     */
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
