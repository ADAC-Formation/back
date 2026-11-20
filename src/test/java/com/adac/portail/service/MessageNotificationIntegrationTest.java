package com.adac.portail.service;

import com.adac.portail.dto.request.MessageFilterType;
import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.repository.NotificationRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Real transaction, real database: the unit tests mock {@link NotificationService}, so they cannot see
 * whether the notification row survives the commit of the message it announces.
 */
@SpringBootTest
@ActiveProfiles("dev")
class MessageNotificationIntegrationTest {

    private static final String EMAIL_PATTERN = "msg-notif-%@adac.fr";

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    private User sender;
    private User firstRecipient;
    private User secondRecipient;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        deleteTestData();
        sender = saveUser("msg-notif-sender@adac.fr", Role.SUPER_ADMIN);
        firstRecipient = saveUser("msg-notif-first@adac.fr", Role.STAGIAIRE);
        secondRecipient = saveUser("msg-notif-second@adac.fr", Role.STAGIAIRE);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    private User saveUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("irrelevant")
                .nom("Notif")
                .prenom("Test")
                .role(role)
                .isActive(true)
                .build());
    }

    private void deleteTestData() {
        String testUsers = "select id from users where email like '" + EMAIL_PATTERN + "'";
        jdbcTemplate.update("delete from notifications where recipient_id in (" + testUsers + ")");
        jdbcTemplate.update("delete from message_recipients where recipient_id in (" + testUsers + ")"
                + " or message_id in (select id from messages where sender_id in (" + testUsers + "))");
        jdbcTemplate.update("delete from messages where sender_id in (" + testUsers + ")");
        jdbcTemplate.update("delete from users where email like '" + EMAIL_PATTERN + "'");
    }

    private List<Notification> notificationsOf(User user) {
        return notificationRepository.findTop200ByRecipientOrderByCreatedAtDesc(user);
    }

    @Test
    void anIndividualMessagePersistsANewMessageNotificationForTheRecipient() {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Bonjour");
        request.setRecipientIds(List.of(firstRecipient.getId()));

        messageService.sendMessage(new AdacUserDetails(sender), request);

        assertThat(notificationsOf(firstRecipient))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.NEW_MESSAGE);
    }

    @Test
    void aGroupMessagePersistsOneNewMessageNotificationPerRecipient() {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Bonjour à tous");
        request.setFilter(new SendMessageRequest.Filter(
                MessageFilterType.MANUAL, null, List.of(firstRecipient.getId(), secondRecipient.getId())));

        messageService.sendMessage(new AdacUserDetails(sender), request);

        assertThat(notificationsOf(firstRecipient))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.NEW_MESSAGE);
        assertThat(notificationsOf(secondRecipient))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.NEW_MESSAGE);
    }

    @Test
    void theSenderDoesNotNotifyThemselves() {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Bonjour");
        request.setRecipientIds(List.of(firstRecipient.getId()));

        messageService.sendMessage(new AdacUserDetails(sender), request);

        assertThat(notificationsOf(sender)).isEmpty();
    }
}
