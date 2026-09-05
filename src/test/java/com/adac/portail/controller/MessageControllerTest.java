package com.adac.portail.controller;

import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link MessageController} — TICKET-029. No method security here (see the
 * controller's Javadoc), so no {@code @EnableMethodSecurity} test config is needed —
 * {@link WithMockAdacUser} alone is enough to give {@code @AuthenticationPrincipal} a real
 * {@link AdacUserDetails} (see its Javadoc for why {@code @WithMockUser} can't) — and, branch-wide
 * review: every test below that reaches the service asserts the exact principal with
 * {@code eq(...)}, not {@code any()}, so a broken principal binding would fail loudly here instead
 * of passing silently (the same regression class {@code WithMockAdacUser} itself exists to catch).
 */
@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static AdacUserDetails currentPrincipal() {
        return (AdacUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // --- GET /api/messages ----------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getConversationsReturnsListFromServiceForTheRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(messageService.getConversations(eq(principal))).thenReturn(
                List.of(ConversationResponse.builder().conversationId(2L).build()));

        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // --- GET /api/messages/{conversationId} ---------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getConversationMessagesReturnsThreadFromServiceForTheRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(messageService.getConversationMessages(eq(principal), eq(2L))).thenReturn(
                List.of(MessageResponse.builder().id(10L).build()));

        mockMvc.perform(get("/api/messages/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // Branch-wide review: an unknown conversationId is not a 404 (would let any authenticated
    // caller enumerate the user-id space) — the service returns an empty list instead.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getConversationMessagesWithNoSharedHistoryReturnsEmptyListNotNotFound() throws Exception {
        when(messageService.getConversationMessages(any(), eq(404L))).thenReturn(List.of());

        mockMvc.perform(get("/api/messages/404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- POST /api/messages/send -------------------------------------------------------------

    // Ticket Test 1: SUPER_ADMIN -> STAGIAIRE -> 201.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendMessageBySuperAdminReturnsCreated() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(messageService.sendMessage(eq(principal), any())).thenReturn(MessageResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Salut", "recipientIds", List.of(2)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    // Ticket Test 2: STAGIAIRE -> STAGIAIRE -> 403 (mapped from the service's UnauthorizedException
    // — the real role-matrix coverage lives in MessageServiceImplTest; this only proves the
    // controller/GlobalExceptionHandler wiring).
    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void sendMessageMapsUnauthorizedExceptionToForbidden() throws Exception {
        doThrow(new UnauthorizedException("Vous ne pouvez pas écrire à ce destinataire"))
                .when(messageService).sendMessage(any(), any());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Salut", "recipientIds", List.of(3)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendMessageWithGroupSendNotYetSupportedReturnsBadRequest() throws Exception {
        doThrow(new BadRequestException("recipientIds doit contenir exactement un destinataire"))
                .when(messageService).sendMessage(any(), any());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("content", "Salut", "filter", Map.of("type", "FORMATION", "formationId", 1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendMessageWithBlankContentReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "", "recipientIds", List.of(2)))))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendMessageWithNullRecipientIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Salut\",\"recipientIds\":[null]}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
    }

    // --- PATCH /api/messages/{id}/read --------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void markAsReadCallsServiceWithTheRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();

        mockMvc.perform(patch("/api/messages/10/read"))
                .andExpect(status().isOk());

        verify(messageService).markAsRead(eq(principal), eq(10L));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void markAsReadWithUnknownMessageReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Message introuvable"))
                .when(messageService).markAsRead(any(), eq(404L));

        mockMvc.perform(patch("/api/messages/404/read"))
                .andExpect(status().isNotFound());
    }
}
