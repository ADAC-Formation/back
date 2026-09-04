package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link UserController} — TICKET-019.
 *
 * <p>Endpoints follow docs/tech.md (POST/GET .../formateurs and .../stagiaires, split PATCH
 * .../deactivate + .../reactivate) rather than the generic {@code POST/PATCH /api/users} sketched
 * in docs/tickets/TICKET-019.md — see that file's revision note.</p>
 *
 * <p>Security filters are disabled ({@code addFilters = false}, same as {@code AuthControllerTest})
 * — role enforcement here is {@code @PreAuthorize} (method security), which runs as an AOP
 * proxy around the controller method and doesn't need the servlet filter chain. {@link
 * MethodSecurityTestConfig} turns that on for this slice; {@code SecurityConfig} itself isn't
 * imported (it would drag in the full JWT filter chain and its bean graph). {@code @WithMockUser}
 * populates {@code SecurityContextHolder} directly, independent of filters.</p>
 *
 * <p>{@code JwtAuthorizationFilter} is still a {@code @Component} the slice picks up as a bean
 * (see {@code AuthControllerTest}'s Javadoc for why) — {@code jwtTokenService} and
 * {@code customUserDetailsService} below only satisfy its construction, never exercised since
 * {@code addFilters = false} skips registering it in the mock chain.</p>
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserControllerTest.MethodSecurityTestConfig.class)
class UserControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // --- POST /formateurs --------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createFormateurBySuperAdminReturnsCreated() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(1L).email("formateur@adac.fr").role(Role.ADMIN).build();
        when(userService.createFormateur(any())).thenReturn(response);

        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "formateur@adac.fr"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("formateur@adac.fr"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createFormateurByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "formateur@adac.fr"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createFormateurWithDuplicateEmailReturnsConflict() throws Exception {
        doThrow(new DuplicateEmailException("Cet email est déjà utilisé"))
                .when(userService).createFormateur(any());

        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "taken@adac.fr"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
    }

    // --- POST /stagiaires --------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createStagiaireBySuperAdminReturnsCreated() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(2L).email("stagiaire@adac.fr").role(Role.STAGIAIRE).build();
        when(userService.createStagiaire(any())).thenReturn(response);

        mockMvc.perform(post("/api/users/stagiaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Martin", "prenom", "Léo", "email", "stagiaire@adac.fr",
                                        "formationIds", List.of(1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAGIAIRE"));
    }

    // --- GET /formateurs, /stagiaires ----------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getFormateursReturnsListFromService() throws Exception {
        when(userService.getFormateurs(isNull(), any())).thenReturn(
                List.of(UserResponse.builder().id(1L).role(Role.ADMIN).build()));

        mockMvc.perform(get("/api/users/formateurs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStagiairesByAdminReturnsOnlyActiveOnes() throws Exception {
        // The active-only restriction itself is enforced in UserServiceImpl (see
        // UserServiceImplTest); this only checks the controller wires the request through.
        when(userService.getStagiaires(isNull(), isNull(), any())).thenReturn(
                List.of(UserResponse.builder().id(5L).role(Role.STAGIAIRE).build()));

        mockMvc.perform(get("/api/users/stagiaires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "STAGIAIRE")
    void getStagiairesByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/stagiaires"))
                .andExpect(status().isForbidden());
    }

    // --- GET /{id} ----------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getByIdReturnsUserResponse() throws Exception {
        when(userService.getById(eq(7L), any())).thenReturn(UserResponse.builder().id(7L).build());

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getByIdNotFoundReturns404() throws Exception {
        when(userService.getById(eq(404L), any())).thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(get("/api/users/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- PATCH /{id}/deactivate, /reactivate ---------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deactivateBySuperAdminReturnsOk() throws Exception {
        when(userService.deactivate(eq(3L), any())).thenReturn(
                UserResponse.builder().id(3L).build());

        mockMvc.perform(patch("/api/users/3/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/3/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deactivateOwnAccountReturnsConflict() throws Exception {
        doThrow(new ConflictException("Vous ne pouvez pas suspendre votre propre compte"))
                .when(userService).deactivate(eq(1L), any());

        mockMvc.perform(patch("/api/users/1/deactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void reactivateBySuperAdminReturnsOk() throws Exception {
        when(userService.reactivate(3L)).thenReturn(
                UserResponse.builder().id(3L).build());

        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void reactivateNeverActivatedAccountReturnsConflict() throws Exception {
        doThrow(new ConflictException("Ce compte n'a jamais été activé"))
                .when(userService).reactivate(3L);

        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
