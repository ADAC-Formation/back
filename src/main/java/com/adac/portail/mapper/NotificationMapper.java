package com.adac.portail.mapper;

import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
