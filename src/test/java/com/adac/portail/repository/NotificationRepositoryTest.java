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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class NotificationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User saveRecipient(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());
    }

    private Notification saveNotification(User recipient, String content, boolean isRead, boolean deletedFromBell) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(NotificationType.NEW_MESSAGE)
                .content(content)
                .isRead(isRead)
                .deletedFromBell(deletedFromBell)
                .build());
    }

    // TICKET-033: the bell view is unread AND not dismissed — neither condition alone is enough.
    @Test
    void bellQueryReturnsOnlyUnreadAndNotDeletedFromBell() {
        User recipient = saveRecipient("notification-repo-bell@adac.fr");
        Notification inBell = saveNotification(recipient, "Non lue, dans la cloche", false, false);
        saveNotification(recipient, "Lue", true, false);
        saveNotification(recipient, "Supprimée de la cloche", false, true);

        List<Notification> result = notificationRepository
                .findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(recipient);

        assertThat(result).extracting(Notification::getId).containsExactly(inBell.getId());
    }

    @Test
    void bellQueryDoesNotReturnAnotherUsersNotifications() {
        User recipient = saveRecipient("notification-repo-bell-owner@adac.fr");
        User other = saveRecipient("notification-repo-bell-other@adac.fr");
        saveNotification(other, "Pas pour moi", false, false);

        assertThat(notificationRepository
                .findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(recipient))
                .isEmpty();
    }

    // Ordering matters — the bell/history both advertise "most recent first" and the frontend
    // relies on it (docs/tech.md § 8); renaming OrderByCreatedAtDesc to Asc would break no test
    // that only checks a single-element result, hence the explicit 3-element check here.
    @Test
    void bellQueryOrdersMostRecentFirst() {
        User recipient = saveRecipient("notification-repo-bell-order@adac.fr");
        Notification first = saveNotification(recipient, "1", false, false);
        Notification second = saveNotification(recipient, "2", false, false);
        Notification third = saveNotification(recipient, "3", false, false);

        List<Notification> result = notificationRepository
                .findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(recipient);

        assertThat(result).extracting(Notification::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void countUnreadInBellMatchesTheQueryResultSize() {
        User recipient = saveRecipient("notification-repo-count@adac.fr");
        saveNotification(recipient, "Non lue 1", false, false);
        saveNotification(recipient, "Non lue 2", false, false);
        saveNotification(recipient, "Lue", true, false);

        assertThat(notificationRepository.countByRecipientAndIsReadFalseAndDeletedFromBellFalse(recipient)).isEqualTo(2);
    }

    // Full history (GET /api/notifications, no filter) keeps everything, including read and
    // dismissed-from-bell notifications — only the bell view excludes them.
    @Test
    void fullHistoryIncludesReadAndDismissedFromBellNotifications() {
        User recipient = saveRecipient("notification-repo-history@adac.fr");
        saveNotification(recipient, "Non lue", false, false);
        saveNotification(recipient, "Lue", true, false);
        saveNotification(recipient, "Supprimée de la cloche", false, true);

        assertThat(notificationRepository.findTop200ByRecipientOrderByCreatedAtDesc(recipient)).hasSize(3);
    }

    // Branch-wide review: this endpoint has no @PreAuthorize at all — the recipient predicate in
    // the query IS the entire access control, so it needs its own proof, not just the bell query's.
    @Test
    void fullHistoryDoesNotReturnAnotherUsersNotifications() {
        User recipient = saveRecipient("notification-repo-history-owner@adac.fr");
        User other = saveRecipient("notification-repo-history-other@adac.fr");
        saveNotification(recipient, "La mienne", false, false);
        saveNotification(other, "Pas pour moi", true, false);

        assertThat(notificationRepository.findTop200ByRecipientOrderByCreatedAtDesc(recipient)).hasSize(1);
    }

    @Test
    void markAllAsReadForRecipientMarksOnlyThatRecipientsUnreadNotifications() {
        User caller = saveRecipient("notification-repo-mark-all@adac.fr");
        User other = saveRecipient("notification-repo-mark-all-other@adac.fr");
        Notification callerUnread1 = saveNotification(caller, "Non lue 1", false, false);
        Notification callerUnread2 = saveNotification(caller, "Non lue 2", false, false);
        Notification callerAlreadyRead = saveNotification(caller, "Déjà lue", true, false);
        Notification othersUnread = saveNotification(other, "Pas la mienne", false, false);

        int updated = notificationRepository.markAllAsReadForRecipient(caller);

        assertThat(updated).isEqualTo(2);
        assertThat(notificationRepository.findById(callerUnread1.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(callerUnread2.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(callerAlreadyRead.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(othersUnread.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void readFilterReturnsOnlyMatchingStatus() {
        User recipient = saveRecipient("notification-repo-read-filter@adac.fr");
        Notification read = saveNotification(recipient, "Lue", true, false);
        saveNotification(recipient, "Non lue", false, false);

        List<Notification> result = notificationRepository
                .findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(recipient, true);

        assertThat(result).extracting(Notification::getId).containsExactly(read.getId());
    }

    @Test
    void readFilterDoesNotReturnAnotherUsersNotifications() {
        User recipient = saveRecipient("notification-repo-read-filter-owner@adac.fr");
        User other = saveRecipient("notification-repo-read-filter-other@adac.fr");
        saveNotification(other, "Pas pour moi", true, false);

        assertThat(notificationRepository.findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(recipient, true))
                .isEmpty();
    }

    // TICKET-033 branch-wide review: fetch + ownership check in one query, replacing a
    // findById-then-compare-in-Java pattern.
    @Test
    void findByIdAndRecipientReturnsEmptyForAnotherUsersNotification() {
        User owner = saveRecipient("notification-repo-owned-owner@adac.fr");
        User other = saveRecipient("notification-repo-owned-other@adac.fr");
        Notification notification = saveNotification(owner, "Pas la tienne", false, false);

        assertThat(notificationRepository.findByIdAndRecipient(notification.getId(), other)).isEmpty();
        assertThat(notificationRepository.findByIdAndRecipient(notification.getId(), owner))
                .map(Notification::getId)
                .contains(notification.getId());
    }

    @Test
    void findByIdAndRecipientReturnsEmptyForAnUnknownId() {
        User owner = saveRecipient("notification-repo-owned-unknown@adac.fr");

        assertThat(notificationRepository.findByIdAndRecipient(404L, owner)).isEqualTo(Optional.empty());
    }
}
