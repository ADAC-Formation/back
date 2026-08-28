package com.adac.portail.mapper;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    // Notification.isRead() (property "read") now matches NotificationResponse's "read"
    // field/accessors — see NotificationResponse.read for why it isn't named isRead.
    NotificationResponse toResponse(Notification notification);
}
