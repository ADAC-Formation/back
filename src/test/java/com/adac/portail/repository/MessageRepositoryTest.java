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
}
