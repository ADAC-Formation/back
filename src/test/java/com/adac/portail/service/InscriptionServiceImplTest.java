package com.adac.portail.service;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.DuplicateInscriptionException;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.InscriptionMapper;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TICKET-023 — see docs/tickets/TICKET-023.md § Write tests first. */
@ExtendWith(MockitoExtension.class)
class InscriptionServiceImplTest {

    @Mock
    private InscriptionRepository inscriptionRepository;

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InscriptionMapper inscriptionMapper;

    @Mock
    private FormationService formationService;

    @InjectMocks
    private InscriptionServiceImpl inscriptionService;

    private static User user(long id, Role role) {
        return user(id, role, true);
    }

    private static User user(long id, Role role, boolean active) {
        return User.builder().id(id).role(role).isActive(active)
                .nom("Nom").prenom("Prenom").email(role.name() + id + "@adac.fr").build();
    }

    // --- getInscriptions -----------------------------------------------------------------------

    // Reuses FormationService.findVisibleFormationOrThrow for the visibility check (review: one
    // lookup, not a discarded DTO + a second findById) — a 404/403 there must surface unchanged.
    @Test
    void getInscriptionsDelegatesVisibilityToFormationService() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Formation formation = Formation.builder().id(1L).build();
        when(formationService.findVisibleFormationOrThrow(1L, principal)).thenReturn(formation);
        Inscription inscription = Inscription.builder().id(1L).formation(formation).build();
        when(inscriptionRepository.findAllByFormation(formation)).thenReturn(List.of(inscription));
        when(inscriptionMapper.toResponse(inscription, 1)).thenReturn(InscriptionResponse.builder().id(1L).build());

        List<InscriptionResponse> result = inscriptionService.getInscriptions(1L, principal);

        assertThat(result).hasSize(1);
        verify(formationRepository, never()).findById(any());
    }

    @Test
    void getInscriptionsPropagatesFormationServiceRejection() {
        AdacUserDetails principal = new AdacUserDetails(user(3L, Role.ADMIN));
        when(formationService.findVisibleFormationOrThrow(1L, principal))
                .thenThrow(new ResourceNotFoundException("Formation introuvable"));

        assertThatThrownBy(() -> inscriptionService.getInscriptions(1L, principal))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).findAllByFormation(any());
    }

    // Review: every row shares the same formation, so its enrollment count is just the list size
    // — no hardcoded 0, no extra query.
    @Test
    void getInscriptionsSetsInscriptionsCountToListSize() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Formation formation = Formation.builder().id(1L).build();
        when(formationService.findVisibleFormationOrThrow(1L, principal)).thenReturn(formation);
        Inscription i1 = Inscription.builder().id(1L).formation(formation).build();
        Inscription i2 = Inscription.builder().id(2L).formation(formation).build();
        when(inscriptionRepository.findAllByFormation(formation)).thenReturn(List.of(i1, i2));
        when(inscriptionMapper.toResponse(any(), eq(2))).thenReturn(InscriptionResponse.builder().build());

        inscriptionService.getInscriptions(1L, principal);

        verify(inscriptionMapper).toResponse(i1, 2);
        verify(inscriptionMapper).toResponse(i2, 2);
        verify(inscriptionRepository, never()).countByFormation(any());
    }

    // --- createInscription ----------------------------------------------------------------------

    @Test
    void createInscriptionEnrollsStagiaireAndReturnsMappedResponse() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        User stagiaire = user(5L, Role.STAGIAIRE);
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(userRepository.findById(5L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(false);
        Inscription saved = Inscription.builder().id(1L).stagiaire(stagiaire).formation(formation).build();
        when(inscriptionRepository.save(any())).thenReturn(saved);
        when(inscriptionRepository.countByFormation(formation)).thenReturn(1L);
        InscriptionResponse expected = InscriptionResponse.builder().id(1L).build();
        when(inscriptionMapper.toResponse(saved, 1)).thenReturn(expected);

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(5L);

        InscriptionResponse result = inscriptionService.createInscription(1L, request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Inscription> captor = ArgumentCaptor.forClass(Inscription.class);
        verify(inscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStagiaire()).isEqualTo(stagiaire);
        assertThat(captor.getValue().getFormation()).isEqualTo(formation);
    }

    // Test 4 (ticket): doublon -> DuplicateInscriptionException.
    @Test
    void createInscriptionAlreadyEnrolledThrows() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        User stagiaire = user(5L, Role.STAGIAIRE);
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(userRepository.findById(5L)).thenReturn(Optional.of(stagiaire));
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(true);

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(5L);

        assertThatThrownBy(() -> inscriptionService.createInscription(1L, request))
                .isInstanceOf(DuplicateInscriptionException.class);

        verify(inscriptionRepository, never()).save(any());
    }

    // Test 5 (ticket): formation archivée -> exception.
    @Test
    void createInscriptionOnArchivedFormationThrows() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ARCHIVED).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(5L);

        assertThatThrownBy(() -> inscriptionService.createInscription(1L, request))
                .isInstanceOf(FormationArchivedException.class);

        verify(inscriptionRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void createInscriptionWithUnknownFormationThrowsResourceNotFound() {
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(5L);

        assertThatThrownBy(() -> inscriptionService.createInscription(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createInscriptionWithUnknownStagiaireIdThrowsResourceNotFound() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(404L);

        assertThatThrownBy(() -> inscriptionService.createInscription(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // A formateur/Super Admin id is not a stagiaire — same reasoning as
    // FormationServiceImpl.findFormateurOrThrow rejecting a stagiaire id, mirrored here.
    @Test
    void createInscriptionWithNonStagiaireIdThrowsResourceNotFound() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(2L);

        assertThatThrownBy(() -> inscriptionService.createInscription(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).save(any());
    }

    // Review: consistent with FormationServiceImpl.findFormateurOrThrow rejecting a deactivated
    // formateur — a deactivated (offboarded) stagiaire must not be enrollable either.
    @Test
    void createInscriptionWithDeactivatedStagiaireThrowsResourceNotFound() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.STAGIAIRE, false)));

        InscriptionRequest request = new InscriptionRequest();
        request.setStagiaireId(5L);

        assertThatThrownBy(() -> inscriptionService.createInscription(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).save(any());
    }

    // --- deleteInscription -----------------------------------------------------------------------

    @Test
    void deleteInscriptionRemovesTheRow() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));

        inscriptionService.deleteInscription(1L, 5L);

        verify(inscriptionRepository).deleteByStagiaire_IdAndFormation_Id(5L, 1L);
    }

    @Test
    void deleteInscriptionIsIdempotentEvenIfNotEnrolled() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));

        // No stubbed existsBy check at all — deleteByStagiaire_IdAndFormation_Id is a no-op
        // derived delete either way (0 rows affected), so this must not throw.
        inscriptionService.deleteInscription(1L, 404L);

        verify(inscriptionRepository).deleteByStagiaire_IdAndFormation_Id(404L, 1L);
    }

    @Test
    void deleteInscriptionWithUnknownFormationThrowsResourceNotFound() {
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inscriptionService.deleteInscription(404L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).deleteByStagiaire_IdAndFormation_Id(any(), any());
    }
}
