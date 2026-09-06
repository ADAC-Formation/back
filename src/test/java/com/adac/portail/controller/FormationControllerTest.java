package com.adac.portail.controller;

import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.InvalidFormationDataException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.FormationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link FormationController} — TICKET-022.
 *
 * <p>Same pattern as {@code UserControllerTest}/{@code CategoryControllerTest}: security filters
 * disabled ({@code addFilters = false}), role enforcement via {@code @PreAuthorize} turned on by
 * {@link MethodSecurityTestConfig}, and {@link WithMockAdacUser} (not {@code @WithMockUser}) so
 * {@code @AuthenticationPrincipal AdacUserDetails} resolves to a real principal instead of
 * {@code null} (TICKET-019 branch-wide review finding).</p>
 */
@WebMvcTest(FormationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FormationControllerTest.MethodSecurityTestConfig.class)
class FormationControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FormationService formationService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> validCreateBody() {
        return Map.of(
                "intitule", "Formation SST",
                "dateDebut", "2026-03-10",
                "dateFin", "2026-03-12",
                "modalite", "PRESENTIEL",
                "categoryId", 1);
    }

    // --- POST /api/formations ------------------------------------------------------------------

    // Test 1 (ticket): SUPER_ADMIN, sans formateurId -> 201, formateurId = SA.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN, id = 9L)
    void createFormationBySuperAdminWithoutFormateurAutoAssignsCaller() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        FormationResponse response = FormationResponse.builder()
                .id(1L).intitule("Formation SST").status(FormationStatus.ACTIVE)
                .category(CategoryResponse.builder().id(1L).build())
                .build();
        when(formationService.createFormation(any(), eq(principal))).thenReturn(response);

        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intitule").value("Formation SST"));

        // Auto-assignment itself (formateur == caller) is FormationServiceImplTest's job — this
        // only checks the controller passes the real principal through, not any().
        verify(formationService).createFormation(any(), eq(principal));
    }

    // Test 2 (ticket): POST by ADMIN -> 403.
    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void createFormationByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateBody())))
                .andExpect(status().isForbidden());

        verify(formationService, never()).createFormation(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void createFormationByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateBody())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createFormationWithoutCategoryIdReturnsBadRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "intitule", "Formation SST",
                "dateDebut", "2026-03-10",
                "dateFin", "2026-03-12",
                "modalite", "PRESENTIEL");

        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(formationService, never()).createFormation(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createFormationWithUnknownCategoryIdReturnsBadRequest() throws Exception {
        doThrow(new InvalidFormationDataException("categoryId introuvable"))
                .when(formationService).createFormation(any(), any());

        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- POST /api/formations/import -----------------------------------------------------------

    // Test 1 (ticket): fichier xlsx valide -> 201.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void importFormationsWithValidXlsxReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "formations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});
        when(formationService.importFormations(any(), any())).thenReturn(
                List.of(FormationResponse.builder().id(1L).build()));

        mockMvc.perform(multipart("/api/formations/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // Test 2 (ticket): fichier pdf -> 400.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void importFormationsWithNonXlsxFileReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "formations.pdf", "application/pdf", new byte[] {1, 2, 3});
        doThrow(new InvalidFormationDataException("Format invalide, seuls les fichiers .xlsx sont acceptés"))
                .when(formationService).importFormations(any(), any());

        mockMvc.perform(multipart("/api/formations/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Format invalide, seuls les fichiers .xlsx sont acceptés"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void importFormationsByAdminReturnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "formations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/formations/import").file(file))
                .andExpect(status().isForbidden());

        verify(formationService, never()).importFormations(any(), any());
    }

    // --- GET /api/formations ---------------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormationsWithNoParamsForwardsNullFilters() throws Exception {
        when(formationService.getFormations(eq(null), eq(null), any())).thenReturn(
                List.of(FormationResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/formations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // AC: ?status=ACTIVE ne retourne que les actives.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormationsWithStatusForwardsTheParam() throws Exception {
        when(formationService.getFormations(eq(FormationStatus.ACTIVE), eq(null), any()))
                .thenReturn(List.of(FormationResponse.builder().id(1L).status(FormationStatus.ACTIVE).build()));

        mockMvc.perform(get("/api/formations").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(formationService).getFormations(eq(FormationStatus.ACTIVE), eq(null), any());
    }

    // AC: ?categoryId=1 -> filtre par catégorie.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormationsWithCategoryIdForwardsTheParam() throws Exception {
        when(formationService.getFormations(eq(null), eq(1L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/formations").param("categoryId", "1"))
                .andExpect(status().isOk());

        verify(formationService).getFormations(eq(null), eq(1L), any());
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN, id = 3L)
    void getFormationsByAdminDelegatesWithRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(formationService.getFormations(eq(null), eq(null), eq(principal))).thenReturn(List.of());

        mockMvc.perform(get("/api/formations")).andExpect(status().isOk());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getFormationsByStagiaireIsAllowed() throws Exception {
        when(formationService.getFormations(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/formations")).andExpect(status().isOk());
    }

    // --- GET /api/formations/{id} -----------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormationByIdReturnsFormationResponse() throws Exception {
        when(formationService.getFormationById(eq(1L), any()))
                .thenReturn(FormationResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/formations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormationByIdNotFoundReturns404() throws Exception {
        when(formationService.getFormationById(eq(404L), any()))
                .thenThrow(new ResourceNotFoundException("Formation introuvable"));

        mockMvc.perform(get("/api/formations/404"))
                .andExpect(status().isNotFound());
    }

    // docs/tech.md: "403 — STAGIAIRE non inscrit".
    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getFormationByIdForNonEnrolledStagiaireReturnsForbidden() throws Exception {
        doThrow(new AccessDeniedException("Accès refusé"))
                .when(formationService).getFormationById(eq(1L), any());

        mockMvc.perform(get("/api/formations/1"))
                .andExpect(status().isForbidden());
    }

    // Review: an ADMIN targeting a formation they don't teach gets the same 404 as an unknown id
    // (FormationServiceImplTest covers the scoping logic itself — this only asserts the exception
    // surfaces as 404 through the controller, not 403 or 500).
    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void getFormationByIdForForeignAdminReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Formation introuvable"))
                .when(formationService).getFormationById(eq(1L), any());

        mockMvc.perform(get("/api/formations/1"))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/formations/{id} -----------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateFormationBySuperAdminReturnsOk() throws Exception {
        when(formationService.updateFormation(eq(1L), any()))
                .thenReturn(FormationResponse.builder().id(1L).intitule("New title").build());

        mockMvc.perform(put("/api/formations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intitule", "New title"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intitule").value("New title"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void updateFormationByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/formations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intitule", "New title"))))
                .andExpect(status().isForbidden());

        verify(formationService, never()).updateFormation(any(), any());
    }

    // Test 4 (ticket): update sur une formation ARCHIVED -> FormationArchivedException -> 400.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateArchivedFormationReturnsBadRequest() throws Exception {
        doThrow(new FormationArchivedException("Formation archivée, modification impossible"))
                .when(formationService).updateFormation(eq(1L), any());

        mockMvc.perform(put("/api/formations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intitule", "New title"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formation archivée, modification impossible"));
    }

    // Review: UpdateFormationRequest.intitule blank must not silently overwrite a valid title.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateFormationWithBlankIntituleReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/formations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intitule", ""))))
                .andExpect(status().isBadRequest());

        verify(formationService, never()).updateFormation(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateFormationWithUnknownIdReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Formation introuvable"))
                .when(formationService).updateFormation(eq(404L), any());

        mockMvc.perform(put("/api/formations/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("intitule", "x"))))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/formations/{id}/archive ----------------------------------------------------

    // Test 3 (ticket): archiveFormation -> status = ARCHIVED, exposed through the controller.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void archiveBySuperAdminReturnsArchivedStatus() throws Exception {
        when(formationService.archiveFormation(1L)).thenReturn(
                FormationResponse.builder().id(1L).status(FormationStatus.ARCHIVED).build());

        mockMvc.perform(patch("/api/formations/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void archiveByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/formations/1/archive"))
                .andExpect(status().isForbidden());

        verify(formationService, never()).archiveFormation(any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void archiveWithUnknownIdReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Formation introuvable"))
                .when(formationService).archiveFormation(404L);

        mockMvc.perform(patch("/api/formations/404/archive"))
                .andExpect(status().isNotFound());
    }

    private static AdacUserDetails currentPrincipal() {
        return (AdacUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
