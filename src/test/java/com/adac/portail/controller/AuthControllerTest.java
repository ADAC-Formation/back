package com.adac.portail.controller;

import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ActivationTokenInvalidException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.service.ActivationService;
import com.adac.portail.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ActivationService activationService;

    @MockitoBean
    private JwtCookieFactory jwtCookieFactory;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutReturnsNoContentAndExpiresJwtCookie() throws Exception {
        ResponseCookie expired = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
        when(jwtCookieFactory.expire()).thenReturn(expired);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("jwt", ""))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(cookie().secure("jwt", false))
                .andExpect(cookie().sameSite("jwt", "Strict"))
                .andExpect(cookie().path("jwt", "/"));
    }

    @Test
    void meWithAuthenticatedPrincipalReturnsUserResponse() throws Exception {
        User user = User.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build();
        AdacUserDetails principal = new AdacUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        when(authService.getCurrentUser(eq(principal))).thenReturn(UserResponse.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("stagiaire@adac.fr"))
                .andExpect(jsonPath("$.role").value("STAGIAIRE"));
    }

    @Test
    void meWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void activateWithValidCodeReturnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "s@adac.fr", "code", "123456", "newPassword", "N3wPassword!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Compte activé avec succès"));

        verify(activationService).activate(any());
    }

    @Test
    void activateWithInvalidCodeReturnsBadRequestViaGlobalExceptionHandler() throws Exception {
        doThrow(new ActivationTokenInvalidException("Code invalide ou expiré"))
                .when(activationService).activate(any());

        mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "s@adac.fr", "code", "000000", "newPassword", "N3wPassword!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Code invalide ou expiré"));
    }

    @Test
    void activateWithMalformedBodyReturnsBadRequestInTheStandardErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "not-an-email", "code", "12", "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").isNotEmpty());

        verify(activationService, never()).activate(any());
    }

    @Test
    void activateWithUnparseableJsonReturnsBadRequestInTheStandardErrorShape() throws Exception {

        mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Requête invalide"));

        verify(activationService, never()).activate(any());
    }

    @Test
    void activateWithWeakPasswordReturnsBadRequest() throws Exception {

        mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "s@adac.fr", "code", "123456", "newPassword", "lowercaseonly"))))
                .andExpect(status().isBadRequest());

        verify(activationService, never()).activate(any());
    }

    @Test
    void resendActivationReturnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/resend-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "s@adac.fr"))))
                .andExpect(status().isOk());

        verify(activationService).resendActivation("s@adac.fr");
    }

    @Test
    void resendActivationOverLimitReturnsTooManyRequests() throws Exception {
        doThrow(new RateLimitException("Trop de demandes. Réessayez dans 15 minutes."))
                .when(activationService).resendActivation(any());

        mockMvc.perform(post("/api/auth/resend-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "s@adac.fr"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void forgotPasswordReturnsSameResponseRegardlessOfWhetherEmailIsKnown() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "known-or-not@adac.fr"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si cet email existe, un code vous a été envoyé."));

        verify(activationService).forgotPassword("known-or-not@adac.fr");
    }

    @Test
    void resetPasswordWithValidCodeReturnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "s@adac.fr", "code", "654321", "newPassword", "An0therPass!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe mis à jour avec succès"));

        verify(activationService).resetPassword(any());
    }

    @Test
    void resetPasswordWithExpiredCodeReturnsBadRequest() throws Exception {
        doThrow(new ActivationTokenInvalidException("Code invalide ou expiré"))
                .when(activationService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "s@adac.fr", "code", "654321", "newPassword", "An0therPass!"))))
                .andExpect(status().isBadRequest());
    }
}
