package com.adac.portail.mapper;

import com.adac.portail.dto.response.UploaderResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "activated", source = "active")
    UserResponse toResponse(User user);

    UploaderResponse toUploader(User user);
}
