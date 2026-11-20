package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.response.AccountImportResponse;
import com.adac.portail.dto.response.ImportErrorResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
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
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.dto.response.UserInscriptionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // NOTES-09 : import de comptes par fichier Excel, réservé à la chargée de formation.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void importAccountsBySuperAdminReturnsCreatedAccountsAndRowErrors() throws Exception {
        UserResponse created = UserResponse.builder().id(5L).email("paul@adac.fr").role(Role.ADMIN).build();
        when(userService.importAccounts(any())).thenReturn(new AccountImportResponse(
                List.of(created), List.of(new ImportErrorResponse(3, "email", "e-mail déjà utilisé"))));

        mockMvc.perform(multipart("/api/users/import")
                        .file(new MockMultipartFile("file", "comptes.xlsx", "application/octet-stream", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created[0].email").value("paul@adac.fr"))
                .andExpect(jsonPath("$.errors[0].ligne").value(3))
                .andExpect(jsonPath("$.errors[0].colonne").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("e-mail déjà utilisé"));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void importAccountsWithABadFileReturnsBadRequest() throws Exception {
        when(userService.importAccounts(any()))
                .thenThrow(new BadRequestException("Format invalide, seuls les fichiers .xlsx sont acceptés"));

        mockMvc.perform(multipart("/api/users/import")
                        .file(new MockMultipartFile("file", "comptes.csv", "text/csv", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Format invalide, seuls les fichiers .xlsx sont acceptés"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void importAccountsByFormateurReturnsForbidden() throws Exception {
        assertImportForbidden();
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void importAccountsByStagiaireReturnsForbidden() throws Exception {
        assertImportForbidden();
    }

    private void assertImportForbidden() throws Exception {
        mockMvc.perform(multipart("/api/users/import")
                        .file(new MockMultipartFile("file", "comptes.xlsx", "application/octet-stream", new byte[] {1})))
                .andExpect(status().isForbidden());
        verify(userService, never()).importAccounts(any());
    }

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

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserBySuperAdminReturnsOk() throws Exception {
        when(userService.updateUser(eq(3L), any(), any())).thenReturn(
                UserResponse.builder().id(3L).nom("Corrigé").build());

        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Corrigé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nom").value("Corrigé"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void updateUserByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Corrigé"))))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(any(), any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void updateUserByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Corrigé"))))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(any(), any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserNotFoundReturns404() throws Exception {
        when(userService.updateUser(eq(404L), any(), any())).thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(put("/api/users/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", "Corrigé"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserWithDuplicateEmailReturnsConflict() throws Exception {
        doThrow(new DuplicateEmailException("Cet email est déjà utilisé"))
                .when(userService).updateUser(eq(3L), any(), any());

        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "taken@adac.fr"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserWithMalformedEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any(), any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserWithBlankEmailReturnsBadRequest() throws Exception {

        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", ""))))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any(), any(), any());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void updateUserWithBlankNomReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nom", ""))))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(any(), any(), any());
    }

    private static AdacUserDetails currentPrincipal() {
        return (AdacUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void authenticateAsStagiaire() {
        User user = User.builder().id(9L).email("stagiaire@adac.fr").role(Role.STAGIAIRE).build();
        AdacUserDetails principal = new AdacUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String oldPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(patch("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("oldPassword", oldPassword, "newPassword", newPassword))));
    }

    @Test
    void changePasswordWithValidBodyReturnsNoContent() throws Exception {
        authenticateAsStagiaire();

        changePassword("Old1password", "New1password").andExpect(status().isNoContent());

        verify(userService).changePassword(any(), any());
    }

    @Test
    void changePasswordWithWeakNewPasswordReturnsBadRequest() throws Exception {
        authenticateAsStagiaire();

        changePassword("Old1password", "weak").andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    void changePasswordWithBlankOldPasswordReturnsBadRequest() throws Exception {
        authenticateAsStagiaire();

        changePassword("", "New1password").andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordWithWrongOldPasswordReturnsForbidden() throws Exception {
        authenticateAsStagiaire();
        doThrow(new UnauthorizedException("Ancien mot de passe incorrect"))
                .when(userService).changePassword(any(), any());

        changePassword("Wrong1password", "New1password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void changePasswordWithoutAuthenticationReturnsUnauthorized() throws Exception {
        changePassword("Old1password", "New1password").andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getUserInscriptionsReturnsTheList() throws Exception {
        when(userService.getInscriptionsOf(9L)).thenReturn(List.of(new UserInscriptionResponse(5L, null)));

        mockMvc.perform(get("/api/users/9/inscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void getUserInscriptionsIsForbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/users/9/inscriptions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void getUserInscriptionsOfUnknownUserReturnsNotFound() throws Exception {
        when(userService.getInscriptionsOf(404L)).thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(get("/api/users/404/inscriptions")).andExpect(status().isNotFound());
    }
}
