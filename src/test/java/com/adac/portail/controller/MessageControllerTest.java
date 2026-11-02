package com.adac.portail.controller;

import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.UserResponse;
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

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getConversationMessagesWithNoSharedHistoryReturnsEmptyListNotNotFound() throws Exception {
        when(messageService.getConversationMessages(any(), eq(404L))).thenReturn(List.of());

        mockMvc.perform(get("/api/messages/404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

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
    void sendMessageWithBothRecipientIdsAndFilterReturnsBadRequest() throws Exception {
        doThrow(new BadRequestException("Fournir exactement un des deux : recipientIds ou filter"))
                .when(messageService).sendMessage(any(), any());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Salut", "recipientIds", List.of(2),
                                "filter", Map.of("type", "FORMATION", "formationId", 1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendGroupMessageWithNullUserIdInFilterReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Salut\",\"filter\":{\"type\":\"MANUAL\",\"userIds\":[null]}}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
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

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void sendGroupMessageWithFormationFilterReturnsCreated() throws Exception {
        when(messageService.sendMessage(any(), any())).thenReturn(MessageResponse.builder().id(1L).group(true).build());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Rappel",
                                "filter", Map.of("type", "FORMATION", "formationId", 1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isGroup").value(true));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void sendGroupMessageWithMissingDocsFilterByAdminReturnsForbidden() throws Exception {
        doThrow(new UnauthorizedException("Réservé au Super Admin"))
                .when(messageService).sendMessage(any(), any());

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Pensez à vos documents",
                                "filter", Map.of("type", "MISSING_DOCS")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void previewGroupRecipientsReturnsListFromService() throws Exception {
        when(messageService.previewGroupRecipients(any(), any()))
                .thenReturn(List.of(UserResponse.builder().id(10L).build()));

        mockMvc.perform(get("/api/messages/group/preview")
                        .param("filterType", "FORMATION")
                        .param("formationId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void previewGroupRecipientsWithMissingDocsFilterByAdminReturnsForbidden() throws Exception {
        doThrow(new UnauthorizedException("Réservé au Super Admin"))
                .when(messageService).previewGroupRecipients(any(), any());

        mockMvc.perform(get("/api/messages/group/preview").param("filterType", "MISSING_DOCS"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void previewGroupRecipientsWithoutFilterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/messages/group/preview"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).previewGroupRecipients(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void previewGroupRecipientsWithInvalidFilterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/messages/group/preview").param("filterType", "BOGUS"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).previewGroupRecipients(any(), any());
    }

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
