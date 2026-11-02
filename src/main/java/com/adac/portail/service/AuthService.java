package com.adac.portail.service;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;

public interface AuthService {

    UserResponse getCurrentUser(AdacUserDetails principal);
}
