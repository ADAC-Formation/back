package com.adac.portail.service;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

public interface NotificationService {

    void notify(Long recipientId, NotificationType type, String content, EntityType entityType, Long entityId);

    List<NotificationResponse> getNotifications(AdacUserDetails principal, Boolean read);

    UnreadNotificationsResponse getUnread(AdacUserDetails principal);

    NotificationResponse markAsRead(AdacUserDetails principal, Long notificationId);

    void markAllAsRead(AdacUserDetails principal);

    void deleteFromBell(AdacUserDetails principal, Long notificationId);
}
