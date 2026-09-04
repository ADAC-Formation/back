package com.adac.portail.service;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public UserResponse getCurrentUser(AdacUserDetails principal) {
        // Callers must have already turned "unauthenticated" into a 401 (see AuthController#me) —
        // this only ever runs for a real principal, so a null here is a caller bug, not a 401.
        Objects.requireNonNull(principal, "principal must not be null — caller must check authentication first");
        return userMapper.toResponse(principal.getUser());
    }
}
