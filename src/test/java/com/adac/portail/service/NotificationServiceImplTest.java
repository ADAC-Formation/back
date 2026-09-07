package com.adac.portail.service;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.NotificationMapper;
import com.adac.portail.repository.NotificationRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TICKET-029 introduced {@code notify(...)}, the one method {@code MessageServiceImpl} needed;
 * TICKET-033 (below) is the full CRUD — see docs/tickets/TICKET-033.md § Write tests first.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private static User user(long id) {
        return User.builder().id(id).nom("Doe").prenom("Jane").role(Role.STAGIAIRE).build();
    }

    private static AdacUserDetails principal(User user) {
        return new AdacUserDetails(user);
    }

    @Test
    void notifySavesNotificationForTheRecipient() {
        User recipient = User.builder().id(5L).role(Role.STAGIAIRE).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(recipient));

        notificationService.notify(5L, NotificationType.NEW_MESSAGE, "Nouveau message de Jane",
                EntityType.MESSAGE, 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isSameAs(recipient);
        assertThat(saved.getType()).isEqualTo(NotificationType.NEW_MESSAGE);
        assertThat(saved.getContent()).isEqualTo("Nouveau message de Jane");
        assertThat(saved.getEntityType()).isEqualTo(EntityType.MESSAGE);
        assertThat(saved.getEntityId()).isEqualTo(1L);
    }

    @Test
    void notifyWithUnknownRecipientThrowsResourceNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.notify(404L, NotificationType.NEW_MESSAGE, "x",
                EntityType.MESSAGE, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getNotifications (TICKET-033) --------------------------------------------------------

    @Test
    void getNotificationsWithNoReadFilterReturnsFullHistory() {
        User caller = user(1L);
        Notification n1 = Notification.builder().id(10L).recipient(caller).build();
        when(notificationRepository.findTop200ByRecipientOrderByCreatedAtDesc(caller)).thenReturn(List.of(n1));
        when(notificationMapper.toResponse(n1)).thenReturn(NotificationResponse.builder().id(10L).build());

        List<NotificationResponse> result = notificationService.getNotifications(principal(caller), null);

        assertThat(result).extracting(NotificationResponse::getId).containsExactly(10L);
    }

    @Test
    void getNotificationsWithReadFilterDelegatesToTheFilteredQuery() {
        User caller = user(1L);
        Notification read = Notification.builder().id(11L).recipient(caller).build();
        when(notificationRepository.findTop200ByRecipientAndIsReadOrderByCreatedAtDesc(caller, true))
                .thenReturn(List.of(read));
        when(notificationMapper.toResponse(read)).thenReturn(NotificationResponse.builder().id(11L).build());

        List<NotificationResponse> result = notificationService.getNotifications(principal(caller), true);

        assertThat(result).extracting(NotificationResponse::getId).containsExactly(11L);
        verify(notificationRepository, never()).findTop200ByRecipientOrderByCreatedAtDesc(any());
    }

    // --- getUnread (TICKET-033) ----------------------------------------------------------------

    @Test
    void getUnreadReturnsCountAndBellNotifications() {
        User caller = user(1L);
        Notification n1 = Notification.builder().id(20L).recipient(caller).build();
        when(notificationRepository.findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(caller))
                .thenReturn(List.of(n1));
        when(notificationRepository.countByRecipientAndIsReadFalseAndDeletedFromBellFalse(caller)).thenReturn(1L);
        when(notificationMapper.toResponse(n1)).thenReturn(NotificationResponse.builder().id(20L).build());

        UnreadNotificationsResponse result = notificationService.getUnread(principal(caller));

        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getNotifications()).extracting(NotificationResponse::getId).containsExactly(20L);
    }

    // Branch-wide review: the badge count must reflect the true total, not just how many of them
    // fit in the capped bell list — proven here with a count that exceeds the list size.
    @Test
    void getUnreadCountCanExceedTheCappedNotificationsList() {
        User caller = user(1L);
        Notification n1 = Notification.builder().id(20L).recipient(caller).build();
        when(notificationRepository.findTop50ByRecipientAndIsReadFalseAndDeletedFromBellFalseOrderByCreatedAtDesc(caller))
                .thenReturn(List.of(n1));
        when(notificationRepository.countByRecipientAndIsReadFalseAndDeletedFromBellFalse(caller)).thenReturn(73L);
        when(notificationMapper.toResponse(n1)).thenReturn(NotificationResponse.builder().id(20L).build());

        UnreadNotificationsResponse result = notificationService.getUnread(principal(caller));

        assertThat(result.getCount()).isEqualTo(73);
        assertThat(result.getNotifications()).hasSize(1);
    }

    // --- markAsRead (TICKET-033) ---------------------------------------------------------------

    @Test
    void markAsReadSetsIsReadTrue() {
        User caller = user(1L);
        Notification notification = Notification.builder().id(30L).recipient(caller).isRead(false).build();
        when(notificationRepository.findByIdAndRecipient(30L, caller)).thenReturn(Optional.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(NotificationResponse.builder().id(30L).build());

        notificationService.markAsRead(principal(caller), 30L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsReadIsIdempotent() {
        User caller = user(1L);
        Notification notification = Notification.builder().id(30L).recipient(caller).isRead(true).build();
        when(notificationRepository.findByIdAndRecipient(30L, caller)).thenReturn(Optional.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(NotificationResponse.builder().id(30L).build());

        notificationService.markAsRead(principal(caller), 30L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsReadWithUnknownNotificationThrowsResourceNotFound() {
        User caller = user(1L);
        when(notificationRepository.findByIdAndRecipient(404L, caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(principal(caller), 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Same oracle-avoidance as MessageService.markAsRead: a notification belonging to someone
    // else is indistinguishable from an unknown id (404, not 403) — findByIdAndRecipient makes
    // this structural: the query itself can't return a foreign row.
    @Test
    void markAsReadOnAnotherUsersNotificationThrowsResourceNotFound() {
        User caller = user(1L);
        when(notificationRepository.findByIdAndRecipient(30L, caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(principal(caller), 30L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- markAllAsRead (TICKET-033, docs/tech.md § 8) ------------------------------------------

    @Test
    void markAllAsReadDelegatesToTheBulkRepositoryUpdateForTheCaller() {
        User caller = user(1L);

        notificationService.markAllAsRead(principal(caller));

        verify(notificationRepository).markAllAsReadForRecipient(caller);
    }

    // --- deleteFromBell (TICKET-033) ------------------------------------------------------------

    @Test
    void deleteFromBellSetsDeletedFromBellTrueWithoutTouchingHistory() {
        User caller = user(1L);
        Notification notification = Notification.builder().id(40L).recipient(caller).deletedFromBell(false).build();
        when(notificationRepository.findByIdAndRecipient(40L, caller)).thenReturn(Optional.of(notification));

        notificationService.deleteFromBell(principal(caller), 40L);

        assertThat(notification.isDeletedFromBell()).isTrue();
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void deleteFromBellIsIdempotent() {
        User caller = user(1L);
        Notification notification = Notification.builder().id(40L).recipient(caller).deletedFromBell(true).build();
        when(notificationRepository.findByIdAndRecipient(40L, caller)).thenReturn(Optional.of(notification));

        notificationService.deleteFromBell(principal(caller), 40L);

        assertThat(notification.isDeletedFromBell()).isTrue();
    }

    @Test
    void deleteFromBellWithUnknownNotificationThrowsResourceNotFound() {
        User caller = user(1L);
        when(notificationRepository.findByIdAndRecipient(404L, caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteFromBell(principal(caller), 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteFromBellOnAnotherUsersNotificationThrowsResourceNotFound() {
        User caller = user(1L);
        when(notificationRepository.findByIdAndRecipient(40L, caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteFromBell(principal(caller), 40L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
