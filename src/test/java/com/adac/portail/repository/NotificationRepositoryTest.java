package com.adac.portail.repository;

import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class NotificationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void findAllByRecipientAndDeletedFromBellFalseExcludesDeletedOnes() {
        User recipient = userRepository.save(User.builder()
                .email("notification-repo-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        Notification visible = notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(NotificationType.NEW_MESSAGE)
                .content("Nouveau message de Marie")
                .build());

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(NotificationType.NEW_MESSAGE)
                .content("Notification supprimée de la cloche")
                .deletedFromBell(true)
                .build());

        List<Notification> result = notificationRepository.findAllByRecipientAndDeletedFromBellFalse(recipient);

        assertThat(result).extracting(Notification::getId).containsExactly(visible.getId());
    }
}
