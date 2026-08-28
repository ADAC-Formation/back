package com.adac.portail.mapper;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // User.isActive() (property "active") now matches UserResponse's "active" field/accessors —
    // see UserResponse.active for why it isn't named isActive. No explicit @Mapping needed.
    UserResponse toResponse(User user);
}
