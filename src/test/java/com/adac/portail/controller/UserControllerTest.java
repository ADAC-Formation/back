package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
 * imported (it would drag in the full JWT filter chain and its bean graph).</p>
 *
 * <p>{@link WithMockAdacUser} (not {@code @WithMockUser}) populates the {@code SecurityContext}
 * with a real {@link AdacUserDetails} — branch-wide review found that {@code @WithMockUser}
 * builds Spring Security's own {@code User} principal, which makes
 * {@code @AuthenticationPrincipal AdacUserDetails principal} resolve to {@code null}
 * (wrong-type). Every test below that reaches the service now asserts the exact principal with
 * {@code eq(...)} instead of {@code any()}/{@code isNull()}, so a broken principal binding fails
 * loudly instead of silently matching.</p>
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- POST /formateurs --------------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
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
    @WithMockAdacUser(role = Role.ADMIN)
    void createFormateurByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "formateur@adac.fr"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void createFormateurByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "formateur@adac.fr"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
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

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createFormateurWithBlankNomReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "", "prenom", "Jane", "email", "formateur@adac.fr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isNotEmpty());

        verify(userService, never()).createFormateur(any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createFormateurWithMalformedEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/formateurs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Doe", "prenom", "Jane", "email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userService, never()).createFormateur(any());
    }

    // --- POST /stagiaires --------------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
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

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void createStagiaireByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/users/stagiaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Martin", "prenom", "Léo", "email", "stagiaire@adac.fr"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void createStagiaireByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/users/stagiaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Martin", "prenom", "Léo", "email", "stagiaire@adac.fr"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createStagiaireWithBlankNomReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/stagiaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "", "prenom", "Léo", "email", "stagiaire@adac.fr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userService, never()).createStagiaire(any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void createStagiaireWithMalformedEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/stagiaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nom", "Martin", "prenom", "Léo", "email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userService, never()).createStagiaire(any());
    }

    // --- GET /formateurs, /stagiaires ----------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN, id = 42L)
    void getFormateursReturnsListFromServiceForTheRealPrincipal() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(userService.getFormateurs(eq(null), eq(principal))).thenReturn(
                List.of(UserResponse.builder().id(1L).role(Role.ADMIN).build()));

        mockMvc.perform(get("/api/users/formateurs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getFormateursWithActiveFalseForwardsTheParamAsIs() throws Exception {
        // Regression test for the branch-wide review finding: ?active=false used to be
        // indistinguishable from an absent param (both collapsed to "no filter" in the service).
        // The controller itself only has to forward the raw value — UserServiceImplTest covers
        // the tri-state filtering logic — but this locks in that "false" isn't silently dropped
        // before it gets there (e.g. by a @RequestParam default or a wrong Boolean overload).
        when(userService.getFormateurs(eq(false), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/formateurs").param("active", "false"))
                .andExpect(status().isOk());

        verify(userService).getFormateurs(eq(false), any());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getFormateursByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/formateurs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN, id = 7L)
    void getStagiairesByAdminDelegatesToServiceForTheRealPrincipal() throws Exception {
        // The active-only restriction and formation ownership are enforced in UserServiceImpl
        // (see UserServiceImplTest) — this only checks the controller forwards the request and
        // the real caller identity through, not any() (branch-wide review: @WithMockUser used to
        // make this untestable since @AuthenticationPrincipal resolved to null under it).
        AdacUserDetails principal = currentPrincipal();
        when(userService.getStagiaires(eq(null), eq(null), eq(principal))).thenReturn(
                List.of(UserResponse.builder().id(5L).role(Role.STAGIAIRE).build()));

        mockMvc.perform(get("/api/users/stagiaires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getStagiairesWithFormationIdForwardsTheParam() throws Exception {
        when(userService.getStagiaires(any(), eq(5L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/stagiaires").param("formationId", "5"))
                .andExpect(status().isOk());

        verify(userService).getStagiaires(any(), eq(5L), any());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getStagiairesByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/stagiaires"))
                .andExpect(status().isForbidden());
    }

    // --- GET /{id} ----------------------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getByIdReturnsUserResponse() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(userService.getById(eq(7L), eq(principal))).thenReturn(UserResponse.builder().id(7L).build());

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getByIdNotFoundReturns404() throws Exception {
        when(userService.getById(eq(404L), any())).thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(get("/api/users/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getByIdByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isForbidden());

        verify(userService, never()).getById(any(), any());
    }

    // --- PATCH /{id}/deactivate, /reactivate ---------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void deactivateBySuperAdminReturnsOk() throws Exception {
        AdacUserDetails principal = currentPrincipal();
        when(userService.deactivate(eq(3L), eq(principal))).thenReturn(
                UserResponse.builder().id(3L).build());

        mockMvc.perform(patch("/api/users/3/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void deactivateByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/3/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void deactivateByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/3/deactivate"))
                .andExpect(status().isForbidden());

        verify(userService, never()).deactivate(any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void deactivateOwnAccountReturnsConflict() throws Exception {
        doThrow(new ConflictException("Vous ne pouvez pas suspendre votre propre compte"))
                .when(userService).deactivate(eq(1L), any());

        mockMvc.perform(patch("/api/users/1/deactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void reactivateBySuperAdminReturnsOk() throws Exception {
        when(userService.reactivate(3L)).thenReturn(
                UserResponse.builder().id(3L).build());

        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void reactivateByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isForbidden());

        verify(userService, never()).reactivate(any());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void reactivateByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isForbidden());

        verify(userService, never()).reactivate(any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void reactivateNeverActivatedAccountReturnsConflict() throws Exception {
        doThrow(new ConflictException("Ce compte n'a jamais été activé"))
                .when(userService).reactivate(3L);

        mockMvc.perform(patch("/api/users/3/reactivate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // --- PATCH /me ------------------------------------------------------------------------
    // TICKET-020. No GET /api/users/me: GET /api/auth/me already returns the caller's full
    // profile (TICKET-014) — see docs/tech.md, "PATCH /api/users/me" note.

    @Test
    void updateMeWithAuthenticatedPrincipalReturnsUpdatedUserResponse() throws Exception {
        User user = User.builder()
                .id(9L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build();
        AdacUserDetails principal = new AdacUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        when(userService.updateMe(eq(principal), any())).thenReturn(UserResponse.builder()
                .id(9L)
                .email("stagiaire@adac.fr")
                .emailNotificationsEnabled(false)
                .build());

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("emailNotificationsEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false));
    }

    @Test
    void updateMeWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("emailNotificationsEnabled", false))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /** The {@link AdacUserDetails} that {@link WithMockAdacUser} put in the SecurityContext for the current test. */
    private static AdacUserDetails currentPrincipal() {
        return (AdacUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
