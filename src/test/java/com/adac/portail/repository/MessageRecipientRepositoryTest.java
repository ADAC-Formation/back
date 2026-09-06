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

/** TICKET-029 branch-wide review — behavioural coverage for the two batched queries {@code MessageServiceImpl} needs. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class MessageRecipientRepositoryTest {

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
    void findAllByMessageInAndRecipientIdInFiltersOutARecipientNotInTheList() {
        User a = save("mr-repo-test-a@adac.fr");
        User b = save("mr-repo-test-b@adac.fr");
        User c = save("mr-repo-test-c@adac.fr");

        Message toB = messageRepository.save(Message.builder().sender(a).content("hi b").build());
        MessageRecipient rowB = messageRecipientRepository.save(MessageRecipient.builder().message(toB).recipient(b).build());
        Message toC = messageRepository.save(Message.builder().sender(a).content("hi c").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(toC).recipient(c).build());

        // Only b is in the candidate list — the row for c must not come back even though toC is
        // in the messages list.
        List<MessageRecipient> result = messageRecipientRepository
                .findAllByMessageInAndRecipientIdIn(List.of(toB, toC), List.of(a.getId(), b.getId()));

        assertThat(result).extracting(MessageRecipient::getId).containsExactly(rowB.getId());
        // JOIN FETCH mr.recipient — touching it outside a transaction elsewhere would otherwise
        // throw LazyInitializationException.
        assertThat(result.get(0).getRecipient().getEmail()).isEqualTo(b.getEmail());
    }

    @Test
    void countUnreadGroupedBySenderReturnsOneRowPerSenderWithOnlyUnreadCounted() {
        User me = save("mr-repo-test-me@adac.fr");
        User senderA = save("mr-repo-test-sender-a@adac.fr");
        User senderB = save("mr-repo-test-sender-b@adac.fr");

        Message fromA1 = messageRepository.save(Message.builder().sender(senderA).content("1").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(fromA1).recipient(me).build());
        Message fromA2 = messageRepository.save(Message.builder().sender(senderA).content("2").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(fromA2).recipient(me).build());

        Message fromBRead = messageRepository.save(Message.builder().sender(senderB).content("3").build());
        messageRecipientRepository.save(MessageRecipient.builder().message(fromBRead).recipient(me)
                .readAt(java.time.OffsetDateTime.now()).build());

        List<MessageRecipientRepository.UnreadCountBySender> result =
                messageRecipientRepository.countUnreadGroupedBySender(me);

        assertThat(result).extracting(
                MessageRecipientRepository.UnreadCountBySender::getSenderId,
                MessageRecipientRepository.UnreadCountBySender::getUnreadCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(senderA.getId(), 2L));
    }
}
