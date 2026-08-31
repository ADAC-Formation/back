package com.adac.portail.mapper;

import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface MessageMapper {

    /**
     * {@code recipients} lives in MessageRecipient, not Message, and {@code readAt} is
     * per-recipient (whoever is viewing) — neither is derivable from a bare Message entity.
     * The service layer (TICKET-029) sets both after calling this mapper. {@code group} maps
     * automatically now (see MessageResponse.group for the isGroup naming note).
     */
    @Mapping(target = "recipients", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    MessageResponse toResponse(Message message);
}
