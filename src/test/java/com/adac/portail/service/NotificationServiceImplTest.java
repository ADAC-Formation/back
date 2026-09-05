package com.adac.portail.service;

import com.adac.portail.entity.Notification;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.repository.NotificationRepository;
import com.adac.portail.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TICKET-029 — minimal slice of the eventual TICKET-033 service: only {@code notify(...)}, the
 * one method {@code MessageServiceImpl} needs. The full CRUD (bell, history, mark-read,
 * delete-from-bell) is TICKET-033's own scope.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

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
}
