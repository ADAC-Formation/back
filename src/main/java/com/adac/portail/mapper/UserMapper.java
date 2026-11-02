package com.adac.portail.mapper;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
