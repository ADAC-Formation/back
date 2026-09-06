package com.adac.portail.controller;

import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.DuplicateInscriptionException;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.InscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link InscriptionController} — TICKET-023. Same pattern as
 * {@code FormationControllerTest}: security filters disabled, {@code @PreAuthorize} turned on by
 * {@link MethodSecurityTestConfig}, {@link WithMockAdacUser} for a real
 * {@code @AuthenticationPrincipal}.
 */
@WebMvcTest(InscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(InscriptionControllerTest.MethodSecurityTestConfig.class)
class InscriptionControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InscriptionService inscriptionService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- GET /api/formations/{id}/inscriptions --------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getInscriptionsReturnsListFromService() throws Exception {
        when(inscriptionService.getInscriptions(eq(1L), any())).thenReturn(
                List.of(InscriptionResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/formations/1/inscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void getInscriptionsForForeignAdminReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Formation introuvable"))
                .when(inscriptionService).getInscriptions(eq(1L), any());

        mockMvc.perform(get("/api/formations/1/inscriptions"))
                .andExpect(status().isNotFound());
    }

    // Review: STAGIAIRE is blocked by @PreAuthorize before reaching the service at all — even an
    // enrolled STAGIAIRE, since InscriptionResponse.stagiaire exposes every co-trainee's email.
    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getInscriptionsByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/formations/1/inscriptions"))
                .andExpect(status().isForbidden());

        verify(inscriptionService, never()).getInscriptions(any(), any());
    }

    // --- POST /api/formations/{id}/inscriptions -------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createInscriptionBySuperAdminReturnsCreated() throws Exception {
        when(inscriptionService.createInscription(eq(1L), any())).thenReturn(
                InscriptionResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/formations/1/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stagiaireId", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void createInscriptionByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/formations/1/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stagiaireId", 5))))
                .andExpect(status().isForbidden());

        verify(inscriptionService, never()).createInscription(any(), any());
    }

    // Test 4 (ticket): doublon -> DuplicateInscriptionException -> 409.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createInscriptionDuplicateReturnsConflict() throws Exception {
        doThrow(new DuplicateInscriptionException("Stagiaire déjà inscrit à cette formation"))
                .when(inscriptionService).createInscription(eq(1L), any());

        mockMvc.perform(post("/api/formations/1/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stagiaireId", 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stagiaire déjà inscrit à cette formation"));
    }

    // Test 5 (ticket): formation archivée -> 400.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createInscriptionOnArchivedFormationReturnsBadRequest() throws Exception {
        doThrow(new FormationArchivedException("Formation archivée, inscription impossible"))
                .when(inscriptionService).createInscription(eq(1L), any());

        mockMvc.perform(post("/api/formations/1/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stagiaireId", 5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createInscriptionWithoutStagiaireIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/formations/1/inscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        verify(inscriptionService, never()).createInscription(any(), any());
    }

    // --- DELETE /api/formations/{id}/inscriptions/{stagiaireId} ---------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void deleteInscriptionBySuperAdminReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/formations/1/inscriptions/5"))
                .andExpect(status().isNoContent());

        verify(inscriptionService).deleteInscription(1L, 5L);
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void deleteInscriptionByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/formations/1/inscriptions/5"))
                .andExpect(status().isForbidden());

        verify(inscriptionService, never()).deleteInscription(any(), any());
    }
}
