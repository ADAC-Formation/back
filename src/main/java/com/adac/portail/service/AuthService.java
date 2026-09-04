package com.adac.portail.service;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;

public interface AuthService {

    /**
     * Maps the currently authenticated principal to the response shape docs/tech.md's
     * {@code GET /api/auth/me} contract expects. Login itself never goes through this service —
     * see {@link com.adac.portail.security.filter.JwtAuthenticationFilter}.
     *
     * @param principal the authenticated user; must not be {@code null} — callers are
     *                  responsible for the 401 case (see {@code AuthController#me})
     */
    UserResponse getCurrentUser(AdacUserDetails principal);
}
