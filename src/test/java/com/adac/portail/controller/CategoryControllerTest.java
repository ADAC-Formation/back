package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateCategoryRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.exception.CategoryAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link CategoryController} — TICKET-047.
 *
 * <p>Security filters are disabled ({@code addFilters = false}, same as {@code UserControllerTest}
 * on {@code feature/users}) — role enforcement is {@code @PreAuthorize} (method security), which
 * doesn't need the servlet filter chain. {@link MethodSecurityTestConfig} turns it on for this
 * slice; {@code SecurityConfig} itself isn't imported.</p>
 *
 * <p>{@code GET /api/categories} has no {@code @PreAuthorize} (any authenticated role may call
 * it — see {@code CategoryController}), so {@code @WithMockUser} without a role is enough there;
 * every other route needs {@code roles = "SUPER_ADMIN"}.</p>
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CategoryControllerTest.MethodSecurityTestConfig.class)
class CategoryControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // --- POST /api/categories ----------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createCategoryBySuperAdminReturnsCreated() throws Exception {
        CategoryResponse response = CategoryResponse.builder()
                .id(1L).nom("Formation SST").couleur("#FF5733").active(true).build();
        when(categoryService.createCategory(any())).thenReturn(response);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "#FF5733"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Formation SST"))
                // AC: "isActive = true par défaut" — asserted at the wire level, not just on the
                // entity (see BooleanFieldJsonContractTest for why @JsonProperty("isActive") is
                // load-bearing here: a plain "active" field would pass every other test silently).
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createCategoryRequestReachesServiceWithBothFields() throws Exception {
        when(categoryService.createCategory(any())).thenReturn(CategoryResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "#FF5733"))))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCategoryRequest> captor = ArgumentCaptor.forClass(CreateCategoryRequest.class);
        verify(categoryService).createCategory(captor.capture());
        assertThat(captor.getValue().getNom()).isEqualTo("Formation SST");
        assertThat(captor.getValue().getCouleur()).isEqualTo("#FF5733");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategoryByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "#FF5733"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAGIAIRE")
    void createCategoryByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "#FF5733"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createCategoryWithDuplicateNameReturnsConflict() throws Exception {
        doThrow(new CategoryAlreadyExistsException("Cette catégorie existe déjà"))
                .when(categoryService).createCategory(any());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "#FF5733"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                // docs/tech.md § 3 pins this exact message on the 409 body.
                .andExpect(jsonPath("$.message").value("Cette catégorie existe déjà"));
    }

    // Test 4 (ticket): invalid couleur → 400.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createCategoryWithInvalidCouleurReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Formation SST", "couleur", "rouge"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(categoryService, never()).createCategory(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createCategoryWithBlankNomReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "", "couleur", "#FF5733"))))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).createCategory(any());
    }

    // --- GET /api/categories -----------------------------------------------------------------

    @Test
    @WithMockUser
    void getCategoriesWithNoParamReturnsAllFromService() throws Exception {
        when(categoryService.getCategories(null)).thenReturn(
                List.of(CategoryResponse.builder().id(1L).build(), CategoryResponse.builder().id(2L).build()));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // Test 6 (ticket): ?active=true returns only active categories.
    @Test
    @WithMockUser
    void getCategoriesWithActiveTrueForwardsTheParam() throws Exception {
        when(categoryService.getCategories(eq(true))).thenReturn(
                List.of(CategoryResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/categories").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(categoryService).getCategories(eq(true));
    }

    // --- PUT /api/categories/{id} ------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateCategoryBySuperAdminReturnsOk() throws Exception {
        when(categoryService.updateCategory(eq(1L), any())).thenReturn(
                CategoryResponse.builder().id(1L).nom("New name").build());

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "New name", "couleur", "#111111"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("New name"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategoryByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "New name", "couleur", "#111111"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateCategoryWithNameTakenReturnsConflict() throws Exception {
        doThrow(new CategoryAlreadyExistsException("Cette catégorie existe déjà"))
                .when(categoryService).updateCategory(eq(1L), any());

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Taken", "couleur", "#111111"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateCategoryWithUnknownIdReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Catégorie introuvable"))
                .when(categoryService).updateCategory(eq(404L), any());

        mockMvc.perform(put("/api/categories/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "x", "couleur", "#111111"))))
                .andExpect(status().isNotFound());
    }

    // Mirrors the equivalent POST tests — PUT carries the same @Valid contract on
    // UpdateCategoryRequest and must not silently reach the service with an invalid body.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateCategoryWithInvalidCouleurReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "New name", "couleur", "rouge"))))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).updateCategory(any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateCategoryWithBlankNomReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "", "couleur", "#111111"))))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).updateCategory(any(), any());
    }

    // --- PATCH /api/categories/{id}/activate, /deactivate -------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deactivateBySuperAdminReturnsOk() throws Exception {
        when(categoryService.deactivateCategory(1L)).thenReturn(
                CategoryResponse.builder().id(1L).build());

        mockMvc.perform(patch("/api/categories/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/categories/1/deactivate"))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).deactivateCategory(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void activateBySuperAdminReturnsOk() throws Exception {
        when(categoryService.activateCategory(1L)).thenReturn(
                CategoryResponse.builder().id(1L).build());

        mockMvc.perform(patch("/api/categories/1/activate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAGIAIRE")
    void activateByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/categories/1/activate"))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).activateCategory(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void activateWithUnknownIdReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Catégorie introuvable"))
                .when(categoryService).activateCategory(404L);

        mockMvc.perform(patch("/api/categories/404/activate"))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/categories/{id} — must not exist ------------------------------------------

    // AC: "Aucun endpoint DELETE /api/categories/{id} n'existe" — categories are never deleted,
    // only deactivated (Category's Javadoc: a formation's FK must always resolve). This is the
    // one explicitly negative acceptance criterion in the ticket; without a test, a future PR
    // could add @DeleteMapping and nothing here would catch it.
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deleteEndpointDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isMethodNotAllowed());
    }
}
