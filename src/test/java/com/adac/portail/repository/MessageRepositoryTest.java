package com.adac.portail.repository;

import com.adac.portail.entity.Message;
import com.adac.portail.entity.MessageRecipient;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural coverage for {@link MessageRepository#findConversationBetween}: bidirectionality,
 * DISTINCT actually deduplicating a group message with several recipients, ascending order, and
 * — the access-control-shaped case — that an unrelated third party's messages don't leak into a
 * thread they aren't part of.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class MessageRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageRecipientRepository messageRecipientRepository;

    private User save(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());
    }

    @Test
    void findConversationBetweenReturnsBothDirectionsInAscendingOrderWithoutDuplicatesOrLeaks() {
        User a = save("message-repo-test-a@adac.fr");
        User b = save("message-repo-test-b@adac.fr");
        User c = save("message-repo-test-c@adac.fr");

        // A -> B (individual)
        Message aToB = messageRepository.save(Message.builder().sender(a).content("Salut B").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToB).recipient(b).build());

        // B -> A (individual, later)
        Message bToA = messageRepository.save(Message.builder().sender(b).content("Salut A").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(bToA).recipient(a).build());

        // A -> {B, C} (group message, later still) — must appear exactly once in the A/B thread,
        // and must not leak an A/C-only message into the A/B thread.
        Message aToGroup = messageRepository.save(Message.builder().sender(a).content("Salut tous").isGroup(true).build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToGroup).recipient(b).build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToGroup).recipient(c).build());

        // A -> C only (unrelated to the A/B thread)
        Message aToC = messageRepository.save(Message.builder().sender(a).content("Juste pour C").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToC).recipient(c).build());

        List<Message> thread = messageRepository.findConversationBetween(a.getId(), b.getId());

        assertThat(thread).extracting(Message::getId)
                .containsExactly(aToB.getId(), bToA.getId(), aToGroup.getId());
        assertThat(thread).extracting(Message::getId).doesNotContain(aToC.getId());
    }

    // TICKET-029: findConversationPartnerIds backs GET /api/messages (the conversations list).
    @Test
    void findConversationPartnerIdsReturnsEachDistinctCorrespondentOnceInEitherDirection() {
        User a = save("message-repo-test-partners-a@adac.fr");
        User b = save("message-repo-test-partners-b@adac.fr");
        User c = save("message-repo-test-partners-c@adac.fr");
        User unrelated = save("message-repo-test-partners-unrelated@adac.fr");

        // A -> B twice (must still count as one partner, not two).
        Message aToB1 = messageRepository.save(Message.builder().sender(a).content("1").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToB1).recipient(b).build());
        Message aToB2 = messageRepository.save(Message.builder().sender(a).content("2").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToB2).recipient(b).build());

        // C -> A (other direction).
        Message cToA = messageRepository.save(Message.builder().sender(c).content("3").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(cToA).recipient(a).build());

        // B <-> unrelated: must not leak into A's partner list.
        Message bToUnrelated = messageRepository.save(Message.builder().sender(b).content("4").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(bToUnrelated).recipient(unrelated).build());

        List<Long> partnerIds = messageRepository.findConversationPartnerIds(a.getId());

        assertThat(partnerIds).containsExactlyInAnyOrder(b.getId(), c.getId());
        assertThat(partnerIds).doesNotContain(unrelated.getId());
    }

    // TICKET-029 branch-wide review: findLastMessageIdPerPartner backs GET /api/messages without
    // the N+1 the original per-partner loop had.
    @Test
    void findLastMessageIdPerPartnerReturnsTheNewestMessageIdForEachPartner() {
        User a = save("message-repo-test-lastmsg-a@adac.fr");
        User b = save("message-repo-test-lastmsg-b@adac.fr");
        User c = save("message-repo-test-lastmsg-c@adac.fr");

        Message aToB1 = messageRepository.save(Message.builder().sender(a).content("older").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToB1).recipient(b).build());
        Message aToB2 = messageRepository.save(Message.builder().sender(a).content("newer").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(aToB2).recipient(b).build());

        Message cToA = messageRepository.save(Message.builder().sender(c).content("only").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(cToA).recipient(a).build());

        List<MessageRepository.PartnerLastMessageId> result =
                messageRepository.findLastMessageIdPerPartner(a.getId(), List.of(b.getId(), c.getId()));

        assertThat(result).extracting(MessageRepository.PartnerLastMessageId::getPartnerId, MessageRepository.PartnerLastMessageId::getMessageId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(b.getId(), aToB2.getId()),
                        org.assertj.core.groups.Tuple.tuple(c.getId(), cToA.getId()));
    }

    @Test
    void findAllByIdWithSenderFetchesSenderEagerly() {
        User a = save("message-repo-test-fetch-a@adac.fr");
        User b = save("message-repo-test-fetch-b@adac.fr");
        Message message = messageRepository.save(Message.builder().sender(a).content("hi").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(message).recipient(b).build());

        List<Message> result = messageRepository.findAllByIdWithSender(List.of(message.getId()));

        assertThat(result).hasSize(1);
        // Touching a field on the sender proxy — would throw LazyInitializationException outside
        // a transaction if it weren't fetched eagerly (same class of bug as InscriptionRepository's).
        assertThat(result.get(0).getSender().getEmail()).isEqualTo(a.getEmail());
    }
}
