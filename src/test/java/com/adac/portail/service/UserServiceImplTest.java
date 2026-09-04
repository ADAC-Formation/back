package com.adac.portail.service;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

/**
 * TICKET-019 — endpoints below follow docs/tech.md (POST/GET /api/users/formateurs and
 * /stagiaires, split PATCH .../deactivate + .../reactivate), not the older generic
 * {@code POST/PATCH /api/users} sketched in docs/tickets/TICKET-019.md — see that file's
 * revision note. Several cases below (formationId ownership scoping, self-suspension, last
 * SUPER_ADMIN, reactivating a never-activated account) were added during the ticket's
 * pre-commit `/review-code` pass, not the ticket's original 5 listed tests.
 */
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivationService activationService;

    @InjectMocks
    private UserServiceImpl userService;

    // --- createFormateur --------------------------------------------------------------------

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

    // --- createStagiaire --------------------------------------------------------------------

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

    @Test
    void createStagiaireWithNoFormationIdsSkipsEnrollment() {
        CreateUserRequest request = new CreateUserRequest("Martin", "Léo", "stagiaire@adac.fr", null);
        when(userRepository.findByEmail("stagiaire@adac.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userService.createStagiaire(request);

        verify(inscriptionRepository, never()).save(any());
    }

    // --- getFormateurs ----------------------------------------------------------------------

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

        // active=false requested, but ADMIN's view is hard-restricted to active-only (docs/tech.md).
        List<UserResponse> result = userService.getFormateurs(false, new AdacUserDetails(admin));

        assertThat(result).hasSize(1);
        verify(userMapper, never()).toResponse(suspendedFormateur);
    }

    // --- getStagiaires ----------------------------------------------------------------------

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

    // --- getById ------------------------------------------------------------------------------

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

    // --- deactivate / reactivate --------------------------------------------------------------

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

    // --- updateMe ---------------------------------------------------------------------------
    // TICKET-020.

    @Test
    void updateMeWithEmailNotificationsEnabledFalsePersistsField() {
        // Two distinct instances on purpose: principal.getUser() stands in for the detached
        // snapshot loaded by CustomUserDetailsService in the filter's own transaction (see
        // updateMe's Javadoc/comment — review found the field was mutated on this stale instance
        // and merge()d instead of on a freshly-fetched managed one). Only the managed instance
        // below should end up mutated and saved.
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
}
