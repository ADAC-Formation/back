package com.adac.portail.service;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.request.UpdateUserRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

public interface UserService {

    UserResponse createFormateur(CreateUserRequest request);

    UserResponse createStagiaire(CreateUserRequest request);

    List<UserResponse> getFormateurs(Boolean active, AdacUserDetails principal);

    List<UserResponse> getStagiaires(Boolean active, Long formationId, AdacUserDetails principal);

    UserResponse getById(Long id, AdacUserDetails principal);

    UserResponse deactivate(Long id, AdacUserDetails principal);

    UserResponse reactivate(Long id);

    UserResponse updateMe(AdacUserDetails principal, UpdateProfileRequest request);

    UserResponse updateUser(Long id, UpdateUserRequest request, AdacUserDetails principal);
}
