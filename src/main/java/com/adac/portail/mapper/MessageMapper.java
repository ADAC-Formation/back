package com.adac.portail.mapper;

import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface MessageMapper {

    @Mapping(target = "recipients", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    MessageResponse toResponse(Message message);
}
