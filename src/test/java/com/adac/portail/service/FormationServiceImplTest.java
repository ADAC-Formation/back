package com.adac.portail.service;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.InvalidFormationDataException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.FormationMapper;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.utils.ExcelImportUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TICKET-022/023 — see docs/tickets/TICKET-022.md and TICKET-023.md § Write tests first. */
@ExtendWith(MockitoExtension.class)
class FormationServiceImplTest {

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InscriptionRepository inscriptionRepository;

    @Mock
    private FormationMapper formationMapper;

    @Mock
    private ExcelImportUtil excelImportUtil;

    @InjectMocks
    private FormationServiceImpl formationService;

    private static User user(long id, Role role) {
        return user(id, role, true);
    }

    private static User user(long id, Role role, boolean active) {
        return User.builder().id(id).role(role).isActive(active)
                .nom("Nom").prenom("Prenom").email(role.name() + id + "@adac.fr").build();
    }

    private static CreateFormationRequest createRequest(Long categoryId, Long formateurId) {
        CreateFormationRequest request = new CreateFormationRequest();
        request.setIntitule("Formation SST");
        request.setDateDebut(LocalDate.of(2026, 3, 10));
        request.setDateFin(LocalDate.of(2026, 3, 12));
        request.setModalite(Modalite.PRESENTIEL);
        request.setCategoryId(categoryId);
        request.setFormateurId(formateurId);
        return request;
    }

    // --- createFormation ------------------------------------------------------------------

    // Test 1 (ticket): SUPER_ADMIN, sans formateurId -> formateur = l'appelant.
    @Test
    void createFormationWithoutFormateurAutoAssignsCallerAsFormateur() {
        User superAdmin = user(9L, Role.SUPER_ADMIN);
        AdacUserDetails principal = new AdacUserDetails(superAdmin);
        Category category = Category.builder().id(1L).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        Formation saved = Formation.builder().id(1L).category(category).formateur(superAdmin).build();
        when(formationRepository.save(any())).thenReturn(saved);
        when(inscriptionRepository.countByFormation(saved)).thenReturn(0L);
        FormationResponse expected = FormationResponse.builder().id(1L).build();
        when(formationMapper.toResponse(saved, 0)).thenReturn(expected);

        FormationResponse result = formationService.createFormation(createRequest(1L, null), principal);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Formation> captor = ArgumentCaptor.forClass(Formation.class);
        verify(formationRepository).save(captor.capture());
        assertThat(captor.getValue().getFormateur()).isEqualTo(superAdmin);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(superAdmin);
        assertThat(captor.getValue().getCategory()).isEqualTo(category);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void createFormationWithFormateurIdAssignsThatFormateur() {
        User superAdmin = user(9L, Role.SUPER_ADMIN);
        AdacUserDetails principal = new AdacUserDetails(superAdmin);
        User formateur = user(2L, Role.ADMIN);
        Category category = Category.builder().id(1L).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(userRepository.findById(2L)).thenReturn(Optional.of(formateur));
        Formation saved = Formation.builder().id(1L).category(category).formateur(formateur).build();
        when(formationRepository.save(any())).thenReturn(saved);
        when(inscriptionRepository.countByFormation(saved)).thenReturn(0L);
        when(formationMapper.toResponse(saved, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        formationService.createFormation(createRequest(1L, 2L), principal);

        ArgumentCaptor<Formation> captor = ArgumentCaptor.forClass(Formation.class);
        verify(formationRepository).save(captor.capture());
        assertThat(captor.getValue().getFormateur()).isEqualTo(formateur);
    }

    // AC: categoryId introuvable -> 400 (InvalidFormationDataException).
    @Test
    void createFormationWithUnknownCategoryIdThrows() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> formationService.createFormation(createRequest(404L, null), principal))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    // Review (branch-wide pass): an unknown formateurId used to be a separate 404
    // (ResourceNotFoundException), inconsistent with the STAGIAIRE-id/deactivated cases just below
    // (400) and with docs/tech.md § 4, which documents every formateurId problem as 400.
    @Test
    void createFormationWithUnknownFormateurIdThrowsBadRequest() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).build()));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> formationService.createFormation(createRequest(1L, 404L), principal))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    // Review: "formateur" means an active ADMIN/SUPER_ADMIN everywhere else in this codebase
    // (UserServiceImpl.createFormateur) — a STAGIAIRE id must not silently become one.
    @Test
    void createFormationWithStagiaireAsFormateurIdThrows() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).build()));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.STAGIAIRE)));

        assertThatThrownBy(() -> formationService.createFormation(createRequest(1L, 5L), principal))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    @Test
    void createFormationWithDeactivatedFormateurIdThrows() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN, false)));

        assertThatThrownBy(() -> formationService.createFormation(createRequest(1L, 2L), principal))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    // --- getFormations ----------------------------------------------------------------------

    @Test
    void getFormationsForSuperAdminReturnsEveryFormation() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Formation f1 = Formation.builder().id(1L).status(FormationStatus.ACTIVE)
                .category(Category.builder().id(1L).build()).build();
        Formation f2 = Formation.builder().id(2L).status(FormationStatus.ARCHIVED)
                .category(Category.builder().id(2L).build()).build();
        when(formationRepository.findAll()).thenReturn(List.of(f1, f2));
        when(inscriptionRepository.countByFormation(any())).thenReturn(0L);
        when(formationMapper.toResponse(any(), eq(0))).thenReturn(FormationResponse.builder().build());

        List<FormationResponse> result = formationService.getFormations(null, null, principal);

        assertThat(result).hasSize(2);
        verify(formationRepository, never()).findAllByFormateur(any());
    }

    // AC: ?status=ACTIVE ne retourne que les actives.
    @Test
    void getFormationsFiltersByStatus() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Category category = Category.builder().id(1L).build();
        Formation active = Formation.builder().id(1L).status(FormationStatus.ACTIVE).category(category).build();
        Formation archived = Formation.builder().id(2L).status(FormationStatus.ARCHIVED).category(category).build();
        when(formationRepository.findAll()).thenReturn(List.of(active, archived));
        when(inscriptionRepository.countByFormation(active)).thenReturn(0L);
        when(formationMapper.toResponse(active, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        List<FormationResponse> result = formationService.getFormations(FormationStatus.ACTIVE, null, principal);

        assertThat(result).hasSize(1);
        verify(formationMapper, never()).toResponse(eq(archived), any(Integer.class));
    }

    // AC: ?categoryId=1 ne retourne que les formations de cette catégorie.
    @Test
    void getFormationsFiltersByCategoryId() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Category category1 = Category.builder().id(1L).build();
        Category category2 = Category.builder().id(2L).build();
        Formation inCategory1 = Formation.builder().id(1L).category(category1).build();
        Formation inCategory2 = Formation.builder().id(2L).category(category2).build();
        when(formationRepository.findAll()).thenReturn(List.of(inCategory1, inCategory2));
        when(inscriptionRepository.countByFormation(inCategory1)).thenReturn(0L);
        when(formationMapper.toResponse(inCategory1, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        List<FormationResponse> result = formationService.getFormations(null, 1L, principal);

        assertThat(result).hasSize(1);
    }

    // AC: ADMIN voit ses formations (formateur par défaut).
    @Test
    void getFormationsForAdminReturnsOnlyOwnFormations() {
        User admin = user(3L, Role.ADMIN);
        AdacUserDetails principal = new AdacUserDetails(admin);
        Formation own = Formation.builder().id(1L).formateur(admin).category(Category.builder().id(1L).build()).build();
        when(formationRepository.findAllByFormateur(admin)).thenReturn(List.of(own));
        when(inscriptionRepository.countByFormation(own)).thenReturn(0L);
        when(formationMapper.toResponse(own, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        List<FormationResponse> result = formationService.getFormations(null, null, principal);

        assertThat(result).hasSize(1);
        verify(formationRepository, never()).findAll();
    }

    // AC: STAGIAIRE voit ses inscriptions.
    @Test
    void getFormationsForStagiaireReturnsOnlyEnrolledFormations() {
        User stagiaire = user(5L, Role.STAGIAIRE);
        AdacUserDetails principal = new AdacUserDetails(stagiaire);
        Formation enrolled = Formation.builder().id(1L).category(Category.builder().id(1L).build()).build();
        when(inscriptionRepository.findFormationsByStagiaire(stagiaire)).thenReturn(List.of(enrolled));
        when(inscriptionRepository.countByFormation(enrolled)).thenReturn(0L);
        when(formationMapper.toResponse(enrolled, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        List<FormationResponse> result = formationService.getFormations(null, null, principal);

        assertThat(result).hasSize(1);
        verify(formationRepository, never()).findAll();
        verify(formationRepository, never()).findAllByFormateur(any());
    }

    // --- getFormationById -------------------------------------------------------------------

    @Test
    void getFormationByIdForSuperAdminReturnsFormation() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.countByFormation(formation)).thenReturn(0L);
        FormationResponse expected = FormationResponse.builder().id(1L).build();
        when(formationMapper.toResponse(formation, 0)).thenReturn(expected);

        assertThat(formationService.getFormationById(1L, principal)).isSameAs(expected);
    }

    @Test
    void getFormationByIdWithUnknownIdThrowsResourceNotFound() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> formationService.getFormationById(404L, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Review: an ADMIN who doesn't teach this formation must not be able to read it by id — the
    // same scoping GET /api/formations already applies. 404, not 403, so the response doesn't
    // confirm the formation exists (same reasoning as UserServiceImpl.getById/getStagiaires).
    @Test
    void getFormationByIdForForeignAdminThrowsResourceNotFound() {
        User admin = user(3L, Role.ADMIN);
        AdacUserDetails principal = new AdacUserDetails(admin);
        User someoneElse = user(4L, Role.ADMIN);
        Formation formation = Formation.builder().id(1L).formateur(someoneElse).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));

        assertThatThrownBy(() -> formationService.getFormationById(1L, principal))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscriptionRepository, never()).countByFormation(any());
    }

    @Test
    void getFormationByIdForOwningAdminReturnsFormation() {
        User admin = user(3L, Role.ADMIN);
        AdacUserDetails principal = new AdacUserDetails(admin);
        Formation formation = Formation.builder().id(1L).formateur(admin).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.countByFormation(formation)).thenReturn(0L);
        FormationResponse expected = FormationResponse.builder().id(1L).build();
        when(formationMapper.toResponse(formation, 0)).thenReturn(expected);

        assertThat(formationService.getFormationById(1L, principal)).isSameAs(expected);
    }

    // docs/tech.md: "403 — STAGIAIRE non inscrit".
    @Test
    void getFormationByIdForNonEnrolledStagiaireThrowsAccessDenied() {
        User stagiaire = user(5L, Role.STAGIAIRE);
        AdacUserDetails principal = new AdacUserDetails(stagiaire);
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(false);

        assertThatThrownBy(() -> formationService.getFormationById(1L, principal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getFormationByIdForEnrolledStagiaireReturnsFormation() {
        User stagiaire = user(5L, Role.STAGIAIRE);
        AdacUserDetails principal = new AdacUserDetails(stagiaire);
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(true);
        when(inscriptionRepository.countByFormation(formation)).thenReturn(0L);
        FormationResponse expected = FormationResponse.builder().id(1L).build();
        when(formationMapper.toResponse(formation, 0)).thenReturn(expected);

        assertThat(formationService.getFormationById(1L, principal)).isSameAs(expected);
    }

    // --- updateFormation ----------------------------------------------------------------------

    // Test 4 (ticket): update sur une formation ARCHIVED -> FormationArchivedException.
    @Test
    void updateArchivedFormationThrows() {
        Formation archived = Formation.builder().id(1L).status(FormationStatus.ARCHIVED).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(archived));

        UpdateFormationRequest request = new UpdateFormationRequest();
        request.setIntitule("New title");

        assertThatThrownBy(() -> formationService.updateFormation(1L, request))
                .isInstanceOf(FormationArchivedException.class);

        verify(formationRepository, never()).save(any());
    }

    @Test
    void updateFormationAppliesOnlyProvidedFields() {
        Category originalCategory = Category.builder().id(1L).build();
        Formation existing = Formation.builder()
                .id(1L).intitule("Old").description("Old desc")
                .dateDebut(LocalDate.of(2026, 1, 1)).dateFin(LocalDate.of(2026, 1, 5))
                .modalite(Modalite.PRESENTIEL).status(FormationStatus.ACTIVE)
                .category(originalCategory)
                .build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(formationRepository.save(existing)).thenReturn(existing);
        when(inscriptionRepository.countByFormation(existing)).thenReturn(0L);
        when(formationMapper.toResponse(existing, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        UpdateFormationRequest request = new UpdateFormationRequest();
        request.setIntitule("New title");

        formationService.updateFormation(1L, request);

        assertThat(existing.getIntitule()).isEqualTo("New title");
        assertThat(existing.getDescription()).isEqualTo("Old desc");
        assertThat(existing.getCategory()).isEqualTo(originalCategory);
    }

    @Test
    void updateFormationWithUnknownCategoryIdThrows() {
        Formation existing = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        UpdateFormationRequest request = new UpdateFormationRequest();
        request.setCategoryId(404L);

        assertThatThrownBy(() -> formationService.updateFormation(1L, request))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    @Test
    void updateFormationWithStagiaireAsFormateurIdThrows() {
        Formation existing = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.STAGIAIRE)));

        UpdateFormationRequest request = new UpdateFormationRequest();
        request.setFormateurId(5L);

        assertThatThrownBy(() -> formationService.updateFormation(1L, request))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    @Test
    void updateFormationWithDateFinBeforeDateDebutThrows() {
        Formation existing = Formation.builder().id(1L).status(FormationStatus.ACTIVE)
                .dateDebut(LocalDate.of(2026, 3, 10)).dateFin(LocalDate.of(2026, 3, 12)).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateFormationRequest request = new UpdateFormationRequest();
        request.setDateFin(LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> formationService.updateFormation(1L, request))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }

    @Test
    void updateFormationWithUnknownIdThrowsResourceNotFound() {
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> formationService.updateFormation(404L, new UpdateFormationRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- archiveFormation ---------------------------------------------------------------------

    // Test 3 (ticket): archiveFormation -> status = ARCHIVED.
    @Test
    void archiveFormationSetsStatusToArchived() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ACTIVE).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(formationRepository.save(formation)).thenReturn(formation);
        when(inscriptionRepository.countByFormation(formation)).thenReturn(0L);
        when(formationMapper.toResponse(formation, 0)).thenReturn(
                FormationResponse.builder().id(1L).status(FormationStatus.ARCHIVED).build());

        formationService.archiveFormation(1L);

        assertThat(formation.getStatus()).isEqualTo(FormationStatus.ARCHIVED);
    }

    @Test
    void archiveAlreadyArchivedFormationIsIdempotent() {
        Formation formation = Formation.builder().id(1L).status(FormationStatus.ARCHIVED).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        when(formationRepository.save(formation)).thenReturn(formation);
        when(inscriptionRepository.countByFormation(formation)).thenReturn(0L);
        when(formationMapper.toResponse(formation, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        formationService.archiveFormation(1L);

        assertThat(formation.getStatus()).isEqualTo(FormationStatus.ARCHIVED);
    }

    @Test
    void archiveFormationWithUnknownIdThrowsResourceNotFound() {
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> formationService.archiveFormation(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- importFormations (TICKET-023) --------------------------------------------------------

    // Test 1 (ticket): fichier xlsx valide -> formations créées, liste retournée. ExcelImportUtil's
    // own parsing/validation is ExcelImportUtilTest's job — here it's mocked to isolate
    // "each parsed row becomes a formation, exactly like createFormation".
    @Test
    void importFormationsCreatesOneFormationPerParsedRow() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        MultipartFile file = new MockMultipartFile("file", "formations.xlsx", null, new byte[0]);
        Category category = Category.builder().id(1L).build();
        CreateFormationRequest row1 = createRequest(1L, null);
        CreateFormationRequest row2 = createRequest(1L, null);
        when(excelImportUtil.parse(file)).thenReturn(List.of(row1, row2));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        Formation saved = Formation.builder().id(1L).category(category).formateur(principal.getUser()).build();
        when(formationRepository.save(any())).thenReturn(saved);
        when(inscriptionRepository.countByFormation(saved)).thenReturn(0L);
        when(formationMapper.toResponse(saved, 0)).thenReturn(FormationResponse.builder().id(1L).build());

        List<FormationResponse> result = formationService.importFormations(file, principal);

        assertThat(result).hasSize(2);
        verify(formationRepository, times(2)).save(any());
    }

    @Test
    void importFormationsWithEmptyFileReturnsEmptyList() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        MultipartFile file = new MockMultipartFile("file", "formations.xlsx", null, new byte[0]);
        when(excelImportUtil.parse(file)).thenReturn(List.of());

        assertThat(formationService.importFormations(file, principal)).isEmpty();
        verify(formationRepository, never()).save(any());
    }

    // Test 2 (ticket): ExcelImportUtil's own rejection (wrong format, bad row) must propagate
    // unchanged — no formation created before the whole file is known to be valid.
    @Test
    void importFormationsPropagatesExcelImportUtilRejection() {
        AdacUserDetails principal = new AdacUserDetails(user(9L, Role.SUPER_ADMIN));
        MultipartFile file = new MockMultipartFile("file", "formations.pdf", null, new byte[0]);
        when(excelImportUtil.parse(file)).thenThrow(
                new InvalidFormationDataException("Format invalide, seuls les fichiers .xlsx sont acceptés"));

        assertThatThrownBy(() -> formationService.importFormations(file, principal))
                .isInstanceOf(InvalidFormationDataException.class);

        verify(formationRepository, never()).save(any());
    }
}
