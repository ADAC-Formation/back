package com.adac.portail.service;

import com.adac.portail.dto.request.ChangePasswordRequest;
import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.request.UpdateUserRequest;
import com.adac.portail.dto.response.AccountImportResponse;
import com.adac.portail.dto.response.ImportErrorResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.utils.AccountImportParser;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.security.LoginAttemptService;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.dto.response.UserInscriptionResponse;
import com.adac.portail.mapper.InscriptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private InscriptionRepository inscriptionRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private InscriptionMapper inscriptionMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivationService activationService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AccountImportParser accountImportParser;

    @InjectMocks
    private UserServiceImpl userService;

    // NOTES-09 : les lignes valides deviennent des comptes (e-mail d'activation chacun), les erreurs sont renvoyées.
    @Test
    void importAccountsCreatesEachValidRowAndReturnsTheRowErrors() {
        MockMultipartFile file = new MockMultipartFile("file", "comptes.xlsx", "application/octet-stream", new byte[] {1});
        ImportErrorResponse error = new ImportErrorResponse(4, "email", "e-mail déjà utilisé");
        when(accountImportParser.parse(file)).thenReturn(new AccountImportParser.Result(List.of(
                new AccountImportParser.AccountRow(new CreateUserRequest("Durand", "Paul", "paul@adac.fr", null), Role.ADMIN),
                new AccountImportParser.AccountRow(new CreateUserRequest("Petit", "Léa", "lea@adac.fr", null), Role.STAGIAIRE)),
                List.of(error)));
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation ->
                UserResponse.builder().email(invocation.<User>getArgument(0).getEmail()).build());

        AccountImportResponse response = userService.importAccounts(file);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(User::getRole).containsExactly(Role.ADMIN, Role.STAGIAIRE);
        assertThat(captor.getAllValues()).allSatisfy(user -> assertThat(user.isActive()).isFalse());
        verify(activationService, times(2)).sendActivationCode(any(User.class));
        assertThat(response.created()).extracting(UserResponse::getEmail).containsExactly("paul@adac.fr", "lea@adac.fr");
        assertThat(response.errors()).containsExactly(error);
    }

    @Test
    void createFormateurSavesInactiveAdminAndTriggersActivationEmail() {
        CreateUserRequest request = new CreateUserRequest("Doe", "Jane", "formateur@adac.fr", null);
        when(userRepository.findByEmail("formateur@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userService.createFormateur(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getNom()).isEqualTo("Doe");
        assertThat(saved.getPrenom()).isEqualTo("Jane");
        assertThat(saved.getEmail()).isEqualTo("formateur@adac.fr");
        assertThat(saved.getPasswordHash()).isEqualTo("random-hash");
        verify(activationService).sendActivationCode(saved);
    }

    @Test
    void createFormateurWithDuplicateEmailThrowsAndNeverSaves() {
        CreateUserRequest request = new CreateUserRequest("Doe", "Jane", "taken@adac.fr", null);
        when(userRepository.findByEmail("taken@adac.fr"))
                .thenReturn(Optional.of(User.builder().email("taken@adac.fr").build()));

        assertThatThrownBy(() -> userService.createFormateur(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(activationService, never()).sendActivationCode(any());
    }

    @Test
    void createStagiaireEnrollsInGivenFormationsAndTriggersActivationEmail() {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", List.of(1L, 2L));
        Formation formation1 = Formation.builder().id(1L).build();
        Formation formation2 = Formation.builder().id(2L).build();
        when(userRepository.findByEmail("stagiaire@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(formationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(formation1, formation2));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userService.createStagiaire(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.STAGIAIRE);

        ArgumentCaptor<Inscription> inscriptionCaptor = ArgumentCaptor.forClass(Inscription.class);
        verify(inscriptionRepository, times(2)).save(inscriptionCaptor.capture());
        assertThat(inscriptionCaptor.getAllValues())
                .extracting(Inscription::getFormation)
                .containsExactlyInAnyOrder(formation1, formation2);
        assertThat(inscriptionCaptor.getAllValues())
                .allSatisfy(i -> assertThat(i.getStagiaire()).isEqualTo(userCaptor.getValue()));
        verify(activationService).sendActivationCode(userCaptor.getValue());
    }

    @Test
    void createStagiaireWithUnknownFormationThrowsResourceNotFoundException() {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", List.of(99L));
        when(userRepository.findByEmail("stagiaire@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(formationRepository.findAllById(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> userService.createStagiaire(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createStagiaireInAnArchivedFormationThrowsAndEnrollsNothing() {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", List.of(1L, 2L));
        Formation active = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        Formation archived = Formation.builder().id(2L).status(FormationStatus.ARCHIVED).build();
        when(userRepository.findByEmail("stagiaire@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(formationRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(active, archived));

        assertThatThrownBy(() -> userService.createStagiaire(request))
                .isInstanceOf(FormationArchivedException.class)
                .hasMessage("Formation archivée, inscription impossible");

        verify(inscriptionRepository, never()).save(any());
        verify(activationService, never()).sendActivationCode(any());
    }

    @Test
    void createStagiaireWithDuplicateFormationIdsEnrollsOnlyOnce() {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", List.of(1L, 1L));
        Formation formation1 = Formation.builder().id(1L).build();
        when(userRepository.findByEmail("stagiaire@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(formationRepository.findAllById(List.of(1L))).thenReturn(List.of(formation1));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userService.createStagiaire(request);

        verify(inscriptionRepository, times(1)).save(any());
    }

    // NOTES-25 (décision de Manon, 2026-11-28) : un stagiaire créé à la main a toujours au moins une formation.
    // L'import Excel (POST /api/users/import) reste l'exception : il crée des comptes sans inscription.
    @ParameterizedTest
    @NullAndEmptySource
    void createStagiaireWithoutFormationThrowsBadRequestAndCreatesNothing(List<Long> formationIds) {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", formationIds);

        assertThatThrownBy(() -> userService.createStagiaire(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Au moins une formation est obligatoire");

        verify(userRepository, never()).save(any());
        verify(inscriptionRepository, never()).save(any());
        verify(activationService, never()).sendActivationCode(any());
    }

    @Test
    void getFormateursForSuperAdminWithoutActiveFilterReturnsAll() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        User activeFormateur = User.builder().isActive(true).build();
        User suspendedFormateur = User.builder().isActive(false).build();
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(activeFormateur, suspendedFormateur));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getFormateurs(null, new AdacUserDetails(superAdmin));

        assertThat(result).hasSize(2);
    }

    @Test
    void getFormateursForSuperAdminWithActiveTrueFiltersSuspendedOut() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        User activeFormateur = User.builder().isActive(true).build();
        User suspendedFormateur = User.builder().isActive(false).build();
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(activeFormateur, suspendedFormateur));
        when(userMapper.toResponse(activeFormateur)).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getFormateurs(true, new AdacUserDetails(superAdmin));

        assertThat(result).hasSize(1);
        verify(userMapper, never()).toResponse(suspendedFormateur);
    }

    @Test
    void getFormateursForAdminAlwaysReturnsOnlyActiveRegardlessOfParam() {
        User admin = User.builder().role(Role.ADMIN).build();
        User activeFormateur = User.builder().isActive(true).build();
        User suspendedFormateur = User.builder().isActive(false).build();
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(activeFormateur, suspendedFormateur));
        when(userMapper.toResponse(activeFormateur)).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getFormateurs(false, new AdacUserDetails(admin));

        assertThat(result).hasSize(1);
        verify(userMapper, never()).toResponse(suspendedFormateur);
    }

    @Test
    void getStagiairesForSuperAdminWithoutFiltersReturnsAllStagiaires() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        User activeStagiaire = User.builder().isActive(true).build();
        User suspendedStagiaire = User.builder().isActive(false).build();
        when(userRepository.findAllByRole(Role.STAGIAIRE)).thenReturn(List.of(activeStagiaire, suspendedStagiaire));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getStagiaires(null, null, new AdacUserDetails(superAdmin));

        assertThat(result).hasSize(2);
    }

    @Test
    void getStagiairesForAdminReturnsOnlyActiveStagiairesEnrolledInOwnFormations() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        User activeStagiaire = User.builder().isActive(true).build();
        User suspendedStagiaire = User.builder().isActive(false).build();
        when(inscriptionRepository.findStagiairesByFormateur(formateur))
                .thenReturn(List.of(activeStagiaire, suspendedStagiaire));
        when(userMapper.toResponse(activeStagiaire)).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getStagiaires(null, null, new AdacUserDetails(formateur));

        assertThat(result).hasSize(1);
        verify(userMapper, never()).toResponse(suspendedStagiaire);
    }

    @Test
    void getStagiairesForSuperAdminWithFormationIdFilterReturnsAllEnrolled() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        Formation formation = Formation.builder().id(5L).build();
        User activeStagiaire = User.builder().isActive(true).build();
        User suspendedStagiaire = User.builder().isActive(false).build();
        when(formationRepository.findById(5L)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.findStagiairesByFormation(formation))
                .thenReturn(List.of(activeStagiaire, suspendedStagiaire));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getStagiaires(null, 5L, new AdacUserDetails(superAdmin));

        assertThat(result).hasSize(2);
    }

    @Test
    void getStagiairesForAdminWithOwnFormationIdReturnsEnrolled() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        Formation ownFormation = Formation.builder().id(5L).formateur(formateur).build();
        User activeStagiaire = User.builder().isActive(true).build();
        when(formationRepository.findById(5L)).thenReturn(Optional.of(ownFormation));
        when(inscriptionRepository.findStagiairesByFormation(ownFormation)).thenReturn(List.of(activeStagiaire));
        when(userMapper.toResponse(activeStagiaire)).thenReturn(UserResponse.builder().build());

        List<UserResponse> result = userService.getStagiaires(null, 5L, new AdacUserDetails(formateur));

        assertThat(result).hasSize(1);
    }

    @Test
    void getStagiairesForAdminWithForeignFormationIdThrowsResourceNotFoundException() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        User otherFormateur = User.builder().id(2L).role(Role.ADMIN).build();
        Formation foreignFormation = Formation.builder().id(5L).formateur(otherFormateur).build();
        when(formationRepository.findById(5L)).thenReturn(Optional.of(foreignFormation));

        assertThatThrownBy(() -> userService.getStagiaires(null, 5L, new AdacUserDetails(formateur)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).findStagiairesByFormation(any());
    }

    @Test
    void getStagiairesWithUnknownFormationIdThrowsResourceNotFoundException() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getStagiaires(null, 404L, new AdacUserDetails(superAdmin)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdBySuperAdminReturnsMappedUser() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        User user = User.builder().id(7L).role(Role.STAGIAIRE).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        UserResponse expected = UserResponse.builder().id(7L).build();
        when(userMapper.toResponse(user)).thenReturn(expected);

        assertThat(userService.getById(7L, new AdacUserDetails(superAdmin))).isSameAs(expected);
    }

    @Test
    void getByIdWithUnknownIdThrowsResourceNotFoundException() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(404L, new AdacUserDetails(superAdmin)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdByAdminForOwnStagiaireReturnsMappedUser() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        User stagiaire = User.builder().id(7L).role(Role.STAGIAIRE).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, formateur)).thenReturn(true);
        UserResponse expected = UserResponse.builder().id(7L).build();
        when(userMapper.toResponse(stagiaire)).thenReturn(expected);

        assertThat(userService.getById(7L, new AdacUserDetails(formateur))).isSameAs(expected);
    }

    @Test
    void getByIdByAdminForUnrelatedStagiaireThrowsResourceNotFoundException() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        User stagiaire = User.builder().id(7L).role(Role.STAGIAIRE).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, formateur)).thenReturn(false);

        assertThatThrownBy(() -> userService.getById(7L, new AdacUserDetails(formateur)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdByAdminForAnotherFormateurReturnsMappedUserWithoutOwnershipCheck() {
        User formateur = User.builder().id(1L).role(Role.ADMIN).build();
        User otherFormateur = User.builder().id(2L).role(Role.ADMIN).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherFormateur));
        UserResponse expected = UserResponse.builder().id(2L).build();
        when(userMapper.toResponse(otherFormateur)).thenReturn(expected);

        assertThat(userService.getById(2L, new AdacUserDetails(formateur))).isSameAs(expected);
        verify(inscriptionRepository, never()).existsByStagiaireAndFormation_Formateur(any(), any());
    }

    @Test
    void deactivateSetsIsActiveFalseAndSaves() {
        User caller = User.builder().id(1L).role(Role.SUPER_ADMIN).build();
        User target = User.builder().id(3L).role(Role.ADMIN).isActive(true).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.deactivate(3L, new AdacUserDetails(caller));

        assertThat(target.isActive()).isFalse();
        verify(userRepository).save(target);
    }

    @Test
    void deactivateOwnAccountThrowsConflictException() {
        User caller = User.builder().id(1L).role(Role.SUPER_ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> userService.deactivate(1L, new AdacUserDetails(caller)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateLastActiveSuperAdminThrowsConflictException() {
        User caller = User.builder().id(1L).role(Role.SUPER_ADMIN).build();
        User target = User.builder().id(2L).role(Role.SUPER_ADMIN).isActive(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndIsActiveTrue(Role.SUPER_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivate(2L, new AdacUserDetails(caller)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateNonLastSuperAdminSucceeds() {
        User caller = User.builder().id(1L).role(Role.SUPER_ADMIN).build();
        User target = User.builder().id(2L).role(Role.SUPER_ADMIN).isActive(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndIsActiveTrue(Role.SUPER_ADMIN)).thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.deactivate(2L, new AdacUserDetails(caller));

        assertThat(target.isActive()).isFalse();
    }

    @Test
    void reactivateSetsIsActiveTrueAndSaves() {
        User user = User.builder().id(3L).isActive(false).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(activationService.hasEverActivated(user)).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().build());

        userService.reactivate(3L);

        assertThat(user.isActive()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void reactivateNeverActivatedAccountThrowsConflictException() {
        User user = User.builder().id(3L).isActive(false).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(activationService.hasEverActivated(user)).thenReturn(false);

        assertThatThrownBy(() -> userService.reactivate(3L)).isInstanceOf(ConflictException.class);

        assertThat(user.isActive()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMeWithEmailNotificationsEnabledFalsePersistsField() {

        User detachedSnapshot = User.builder().id(9L).emailNotificationsEnabled(true).build();
        User managedUser = User.builder().id(9L).emailNotificationsEnabled(true).build();
        AdacUserDetails principal = new AdacUserDetails(detachedSnapshot);
        UpdateProfileRequest request = new UpdateProfileRequest(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(managedUser));
        when(userRepository.save(managedUser)).thenReturn(managedUser);
        when(userMapper.toResponse(managedUser)).thenReturn(UserResponse.builder().build());

        userService.updateMe(principal, request);

        assertThat(managedUser.isEmailNotificationsEnabled()).isFalse();
        assertThat(detachedSnapshot.isEmailNotificationsEnabled()).isTrue();
        verify(userRepository).save(managedUser);
        verify(userRepository, never()).save(detachedSnapshot);
    }

    @Test
    void updateMeWithNullFieldLeavesEmailNotificationsEnabledUnchanged() {
        User detachedSnapshot = User.builder().id(9L).emailNotificationsEnabled(true).build();
        User managedUser = User.builder().id(9L).emailNotificationsEnabled(true).build();
        AdacUserDetails principal = new AdacUserDetails(detachedSnapshot);
        UpdateProfileRequest request = new UpdateProfileRequest(null);
        when(userRepository.findById(9L)).thenReturn(Optional.of(managedUser));
        when(userRepository.save(managedUser)).thenReturn(managedUser);
        when(userMapper.toResponse(managedUser)).thenReturn(UserResponse.builder().build());

        userService.updateMe(principal, request);

        assertThat(managedUser.isEmailNotificationsEnabled()).isTrue();
    }

    @Test
    void updateUserWithOnlyNomProvidedLeavesPrenomAndEmailUnchanged() {
        User target = User.builder().id(3L).nom("Ancien").prenom("Jane").email("jane@adac.fr")
                .isActive(true).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest("Corrigé", null, null);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.updateUser(3L, request, admin);

        assertThat(target.getNom()).isEqualTo("Corrigé");
        assertThat(target.getPrenom()).isEqualTo("Jane");
        assertThat(target.getEmail()).isEqualTo("jane@adac.fr");
    }

    @Test
    void updateUserWithEmailTakenByAnotherUserThrowsDuplicateEmailException() {
        User target = User.builder().id(3L).email("jane@adac.fr").isActive(true).build();
        User someoneElse = User.builder().id(99L).email("taken@adac.fr").build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "taken@adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("taken@adac.fr")).thenReturn(Optional.of(someoneElse));

        assertThatThrownBy(() -> userService.updateUser(3L, request, admin))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserWithEmailDifferingOnlyByCaseFromAnotherUsersThrowsDuplicateEmailException() {

        User target = User.builder().id(3L).email("mine@adac.fr").isActive(true).build();
        User someoneElse = User.builder().id(99L).email("jane@adac.fr").build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "Jane@Adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("Jane@Adac.fr")).thenReturn(Optional.of(someoneElse));

        assertThatThrownBy(() -> userService.updateUser(3L, request, admin))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateUserWithEmailUnchangedFromCurrentDoesNotThrow() {

        User target = User.builder().id(3L).email("jane@adac.fr").isActive(true).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "jane@adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.updateUser(3L, request, admin);

        assertThat(target.getEmail()).isEqualTo("jane@adac.fr");
        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(activationService, never()).sendActivationCode(any());
    }

    @Test
    void updateUserWithEmailUnchangedButDifferentCaseDoesNotThrow() {
        User target = User.builder().id(3L).email("jane@adac.fr").isActive(true).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "JANE@ADAC.FR");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.updateUser(3L, request, admin);

        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void updateUserOnNeverActivatedAccountWithEmailChangeResendsActivationCodeToNewEmail() {
        User target = User.builder().id(3L).email("old@adac.fr").isActive(false).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "new@adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("new@adac.fr")).thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());
        when(activationService.hasEverActivated(target)).thenReturn(false);

        userService.updateUser(3L, request, admin);

        assertThat(target.getEmail()).isEqualTo("new@adac.fr");

        verify(activationService).sendActivationCode(target);
    }

    @Test
    void updateUserOnAlreadyActivatedAccountWithEmailChangeDoesNotSendAnyMail() {
        User target = User.builder().id(3L).email("old@adac.fr").isActive(true).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "new@adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("new@adac.fr")).thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());
        when(activationService.hasEverActivated(target)).thenReturn(true);

        userService.updateUser(3L, request, admin);

        verify(activationService, never()).sendActivationCode(any());
    }

    @Test
    void updateUserWhenActivationResendIsRateLimitedStillPersistsTheDataChange() {

        User target = User.builder().id(3L).email("old@adac.fr").isActive(false).build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest(null, null, "new@adac.fr");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase("new@adac.fr")).thenReturn(Optional.empty());
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());
        when(activationService.hasEverActivated(target)).thenReturn(false);
        org.mockito.Mockito.doThrow(new RateLimitException("Trop de demandes. Réessayez dans 15 minutes."))
                .when(activationService).sendActivationCode(target);

        UserResponse response = userService.updateUser(3L, request, admin);

        assertThat(response).isNotNull();
        assertThat(target.getEmail()).isEqualTo("new@adac.fr");
        verify(userRepository).save(target);
    }

    @Test
    void updateUserWithUnknownIdThrowsResourceNotFoundException() {
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest("Corrigé", null, null);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(404L, request, admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUserNeverChangesRoleOrIsActiveEvenIfSomehowPresentOnTheEntity() {

        User target = User.builder().id(3L).role(Role.STAGIAIRE).isActive(true).email("s@adac.fr").build();
        AdacUserDetails admin = new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build());
        UpdateUserRequest request = new UpdateUserRequest("Nouveau", null, null);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());

        userService.updateUser(3L, request, admin);

        assertThat(target.getRole()).isEqualTo(Role.STAGIAIRE);
        assertThat(target.isActive()).isTrue();
    }

    private UserResponse getByIdAsSuperAdmin(User target) {
        User superAdmin = User.builder().id(1L).role(Role.SUPER_ADMIN).isActive(true).build();
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userMapper.toResponse(target)).thenReturn(UserResponse.builder().build());
        return userService.getById(target.getId(), new AdacUserDetails(superAdmin));
    }

    @Test
    void activeUserIsActivatedWithoutAnyTokenLookup() {
        User active = User.builder().id(7L).role(Role.STAGIAIRE).isActive(true).build();

        assertThat(getByIdAsSuperAdmin(active).isActivated()).isTrue();
        verify(activationService, never()).hasEverActivated(any());
    }

    @Test
    void suspendedUserWhoActivatedOnceIsActivated() {
        User suspended = User.builder().id(7L).role(Role.STAGIAIRE).isActive(false).build();
        when(activationService.hasEverActivated(suspended)).thenReturn(true);

        assertThat(getByIdAsSuperAdmin(suspended).isActivated()).isTrue();
    }

    @Test
    void neverActivatedUserIsNotActivated() {
        User pending = User.builder().id(7L).role(Role.STAGIAIRE).isActive(false).build();
        when(activationService.hasEverActivated(pending)).thenReturn(false);

        assertThat(getByIdAsSuperAdmin(pending).isActivated()).isFalse();
    }

    private User passwordUser() {
        return User.builder().id(9L).email("me@adac.fr").role(Role.STAGIAIRE).isActive(true)
                .passwordHash("old-hash").build();
    }

    @Test
    void changePasswordWithCorrectOldPasswordStoresNewHash() {
        User user = passwordUser();
        when(loginAttemptService.key("me@adac.fr", "password-change")).thenReturn("k");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old1password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("New1password")).thenReturn("new-hash");

        userService.changePassword(new AdacUserDetails(user), new ChangePasswordRequest("Old1password", "New1password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(loginAttemptService).recordSuccess("k");
    }

    @Test
    void changePasswordWithWrongOldPasswordIsForbiddenAndCountsAsFailure() {
        User user = passwordUser();
        when(loginAttemptService.key("me@adac.fr", "password-change")).thenReturn("k");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong1password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(new AdacUserDetails(user),
                new ChangePasswordRequest("Wrong1password", "New1password")))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
        verify(loginAttemptService).recordFailure("k");
    }

    @Test
    void changePasswordWhileLockedIsRateLimitedWithoutCheckingThePassword() {
        User user = passwordUser();
        when(loginAttemptService.key("me@adac.fr", "password-change")).thenReturn("k");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(loginAttemptService.isLocked("k")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(new AdacUserDetails(user),
                new ChangePasswordRequest("Old1password", "New1password")))
                .isInstanceOf(RateLimitException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getInscriptionsOfMapsEachInscriptionOfTheStagiaire() {
        User stagiaire = User.builder().id(9L).role(Role.STAGIAIRE).build();
        Inscription inscription = Inscription.builder().id(5L).build();
        UserInscriptionResponse mapped = new UserInscriptionResponse(5L, null);
        when(userRepository.findById(9L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.findAllByStagiaire(stagiaire)).thenReturn(List.of(inscription));
        when(inscriptionMapper.toUserInscription(inscription)).thenReturn(mapped);

        assertThat(userService.getInscriptionsOf(9L, new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build())))
                .containsExactly(mapped);
    }

    @Test
    void getInscriptionsOfByAdminKeepsOnlyTheFormationsHeTeaches() {
        User formateur = User.builder().id(2L).role(Role.ADMIN).build();
        User other = User.builder().id(3L).role(Role.ADMIN).build();
        User stagiaire = User.builder().id(9L).role(Role.STAGIAIRE).isActive(true).build();
        Inscription mine = Inscription.builder().id(5L).formation(Formation.builder().id(50L).formateur(formateur).build()).build();
        Inscription theirs = Inscription.builder().id(6L).formation(Formation.builder().id(60L).formateur(other).build()).build();
        UserInscriptionResponse mapped = new UserInscriptionResponse(5L, null);
        when(userRepository.findById(9L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, formateur)).thenReturn(true);
        when(inscriptionRepository.findAllByStagiaire(stagiaire)).thenReturn(List.of(mine, theirs));
        when(inscriptionMapper.toUserInscription(mine)).thenReturn(mapped);

        assertThat(userService.getInscriptionsOf(9L, new AdacUserDetails(formateur))).containsExactly(mapped);
    }

    @Test
    void getInscriptionsOfByAdminForAStagiaireOfAnotherFormateurIsNotFound() {
        User formateur = User.builder().id(2L).role(Role.ADMIN).build();
        User stagiaire = User.builder().id(9L).role(Role.STAGIAIRE).isActive(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, formateur)).thenReturn(false);

        assertThatThrownBy(() -> userService.getInscriptionsOf(9L, new AdacUserDetails(formateur)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(inscriptionRepository, never()).findAllByStagiaire(any());
    }

    @Test
    void getInscriptionsOfUnknownUserIsNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getInscriptionsOf(404L, new AdacUserDetails(User.builder().id(1L).role(Role.SUPER_ADMIN).build()))).isInstanceOf(ResourceNotFoundException.class);
    }
}
