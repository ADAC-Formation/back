package com.adac.portail.service;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.DocumentType;
import com.adac.portail.entity.enums.DeadlineAnchor;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.DocumentTypeAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.DocumentTypeMapper;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.DocumentTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceImplTest {

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DocumentTypeMapper documentTypeMapper;

    @InjectMocks
    private DocumentTypeServiceImpl service;

    /** Une requête valide « toutes les formations, obligatoire », à modifier par chaque test. */
    private static DocumentTypeRequest request() {
        DocumentTypeRequest request = new DocumentTypeRequest();
        request.setNom("Pièce d'identité");
        request.setRequired(true);
        request.setSensitive(false);
        request.setAppliesToAllFormations(true);
        return request;
    }

    private static Category category(long id, String nom) {
        return Category.builder().id(id).nom(nom).couleur("#112233").build();
    }

    private DocumentType created() {
        ArgumentCaptor<DocumentType> captor = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentTypeRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void saveReturnsItsArgument() {
        when(documentTypeRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentTypeMapper.toResponse(any())).thenReturn(DocumentTypeResponse.builder().id(1L).build());
    }

    // --- création -------------------------------------------------------------------------------------------

    @Test
    void createsATypeForAllFormationsWithTheGivenFlags() {
        saveReturnsItsArgument();
        DocumentTypeRequest request = request();
        request.setNom("  Pièce d'identité  ");
        request.setAide("Recto-verso");
        request.setSensitive(true);

        DocumentTypeResponse response = service.createDocumentType(request);

        DocumentType saved = created();
        assertThat(saved.getNom()).isEqualTo("Pièce d'identité");
        assertThat(saved.getAide()).isEqualTo("Recto-verso");
        assertThat(saved.isRequired()).isTrue();
        assertThat(saved.isSensitive()).isTrue();
        assertThat(saved.isAppliesToAll()).isTrue();
        assertThat(saved.getCategories()).isEmpty();
        assertThat(saved.isActive()).isTrue();
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void createsATypeLinkedToTheGivenCategories() {
        saveReturnsItsArgument();
        Category secours = category(1L, "Secourisme");
        Category intra = category(2L, "Formation en intra");
        when(categoryRepository.findAllById(any())).thenReturn(List.of(secours, intra));
        DocumentTypeRequest request = request();
        request.setAppliesToAllFormations(false);
        request.setCategoryIds(List.of(1L, 2L, 2L));

        service.createDocumentType(request);

        assertThat(created().getCategories()).containsExactlyInAnyOrder(secours, intra);
    }

    @Test
    void storesTheDeadlineAndNormalizesTheFormats() {
        saveReturnsItsArgument();
        DocumentTypeRequest request = request();
        request.setDeadlineDays(7);
        request.setDeadlineAnchor(DeadlineAnchor.BEFORE_START);
        request.setAllowedFormats(List.of("PDF", " jpg ", "pdf"));

        service.createDocumentType(request);

        DocumentType saved = created();
        assertThat(saved.getDeadlineDays()).isEqualTo(7);
        assertThat(saved.getDeadlineAnchor()).isEqualTo(DeadlineAnchor.BEFORE_START);
        assertThat(saved.getAllowedFormats()).isEqualTo("pdf,jpg");
    }

    @Test
    void anEmptyFormatListAndABlankHelpTextAreStoredAsNull() {
        saveReturnsItsArgument();
        DocumentTypeRequest request = request();
        request.setAllowedFormats(List.of());
        request.setAide("   ");

        service.createDocumentType(request);

        DocumentType saved = created();
        assertThat(saved.getAllowedFormats()).isNull();
        assertThat(saved.getAide()).isNull();
    }

    @Test
    void refusesANameAlreadyUsedIgnoringCase() {
        when(documentTypeRepository.existsByNomIgnoreCase("Pièce d'identité")).thenReturn(true);

        assertThatThrownBy(() -> service.createDocumentType(request()))
                .isInstanceOf(DocumentTypeAlreadyExistsException.class);

        verify(documentTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void turnsTheDatabaseUniquenessRaceIntoTheSameConflict() {
        when(documentTypeRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_document_types_nom_upper"));

        assertThatThrownBy(() -> service.createDocumentType(request()))
                .isInstanceOf(DocumentTypeAlreadyExistsException.class);
    }

    @Test
    void refusesCategoriesOnATypeForAllFormations() {
        DocumentTypeRequest request = request();
        request.setCategoryIds(List.of(1L));

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(BadRequestException.class);

        verify(documentTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesATypeForCategoriesWithoutAnyCategory() {
        DocumentTypeRequest request = request();
        request.setAppliesToAllFormations(false);
        request.setCategoryIds(null);

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAnUnknownCategory() {
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category(1L, "Secourisme")));
        DocumentTypeRequest request = request();
        request.setAppliesToAllFormations(false);
        request.setCategoryIds(List.of(1L, 99L));

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(ResourceNotFoundException.class);

        verify(documentTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesDeadlineDaysWithoutAnAnchor() {
        DocumentTypeRequest request = request();
        request.setDeadlineDays(7);

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAnAnchorWithoutDeadlineDays() {
        DocumentTypeRequest request = request();
        request.setDeadlineAnchor(DeadlineAnchor.AFTER_END);

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesDeadlineDaysOutsideZeroTo365() {
        DocumentTypeRequest tooLong = request();
        tooLong.setDeadlineDays(366);
        tooLong.setDeadlineAnchor(DeadlineAnchor.AFTER_END);
        DocumentTypeRequest negative = request();
        negative.setDeadlineDays(-1);
        negative.setDeadlineAnchor(DeadlineAnchor.AFTER_END);

        assertThatThrownBy(() -> service.createDocumentType(tooLong)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createDocumentType(negative)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAFormatTheUploadDoesNotAccept() {
        DocumentTypeRequest request = request();
        request.setAllowedFormats(List.of("pdf", "gif"));

        assertThatThrownBy(() -> service.createDocumentType(request)).isInstanceOf(BadRequestException.class);
    }

    // --- modification ---------------------------------------------------------------------------------------

    @Test
    void updateReplacesEveryFieldAndLeavesTheStateUntouched() {
        DocumentType existing = DocumentType.builder().id(5L).nom("Ancien").required(false).appliesToAll(false)
                .categories(new HashSet<>(Set.of(category(1L, "Secourisme"))))
                .deadlineDays(3).deadlineAnchor(DeadlineAnchor.AFTER_END).allowedFormats("pdf").active(false).build();
        when(documentTypeRepository.findById(5L)).thenReturn(Optional.of(existing));
        saveReturnsItsArgument();
        DocumentTypeRequest request = request();
        request.setNom("Nouveau");

        service.updateDocumentType(5L, request);

        assertThat(existing.getNom()).isEqualTo("Nouveau");
        assertThat(existing.isRequired()).isTrue();
        assertThat(existing.isAppliesToAll()).isTrue();
        assertThat(existing.getCategories()).isEmpty();
        assertThat(existing.getDeadlineDays()).isNull();
        assertThat(existing.getDeadlineAnchor()).isNull();
        assertThat(existing.getAllowedFormats()).isNull();
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void updateOfAnUnknownTypeIsNotFound() {
        when(documentTypeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDocumentType(404L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRefusesANameUsedByAnotherType() {
        when(documentTypeRepository.findById(5L))
                .thenReturn(Optional.of(DocumentType.builder().id(5L).nom("A").build()));
        when(documentTypeRepository.existsByNomIgnoreCaseAndIdNot("Pièce d'identité", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateDocumentType(5L, request()))
                .isInstanceOf(DocumentTypeAlreadyExistsException.class);

        verify(documentTypeRepository, never()).saveAndFlush(any());
    }

    // --- activation -----------------------------------------------------------------------------------------

    @Test
    void deactivateAndActivateFlipTheState() {
        DocumentType existing = DocumentType.builder().id(5L).nom("A").active(true).build();
        when(documentTypeRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(documentTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentTypeMapper.toResponse(any())).thenReturn(DocumentTypeResponse.builder().id(5L).build());

        service.deactivateDocumentType(5L);
        assertThat(existing.isActive()).isFalse();

        service.activateDocumentType(5L);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void activationOfAnUnknownTypeIsNotFound() {
        when(documentTypeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateDocumentType(404L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivateDocumentType(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theResponseListsItsCategoriesSortedByName() {
        saveReturnsItsArgument();
        when(documentTypeMapper.toResponse(any())).thenReturn(DocumentTypeResponse.builder().id(1L).categories(List.of(
                CategoryResponse.builder().nom("Méthodologie").build(),
                CategoryResponse.builder().nom("estime de soi").build())).build());

        DocumentTypeResponse response = service.createDocumentType(request());

        assertThat(response.getCategories()).extracting(CategoryResponse::getNom).containsExactly("estime de soi", "Méthodologie");
    }

    // --- liste ----------------------------------------------------------------------------------------------

    @Test
    void listsActiveTypesOnlyWhenAsked() {
        DocumentType active = DocumentType.builder().id(1L).nom("A").build();
        when(documentTypeRepository.findAllByActiveTrue()).thenReturn(List.of(active));
        when(documentTypeMapper.toResponse(active)).thenReturn(DocumentTypeResponse.builder().id(1L).build());

        assertThat(service.getDocumentTypes(true)).hasSize(1);

        verify(documentTypeRepository, never()).findAll();
    }

    @Test
    void listsEveryTypeSortedByNameIgnoringCaseAndAccents() {
        DocumentType zebre = DocumentType.builder().id(1L).nom("zèbre").build();
        DocumentType eleve = DocumentType.builder().id(2L).nom("Élève").build();
        DocumentType attestation = DocumentType.builder().id(3L).nom("Attestation").build();
        when(documentTypeRepository.findAll()).thenReturn(List.of(zebre, eleve, attestation));
        when(documentTypeMapper.toResponse(any())).thenAnswer(invocation -> {
            DocumentType type = invocation.getArgument(0);
            return DocumentTypeResponse.builder().id(type.getId()).nom(type.getNom()).build();
        });

        List<DocumentTypeResponse> result = service.getDocumentTypes(null);

        assertThat(result).extracting(DocumentTypeResponse::getNom).containsExactly("Attestation", "Élève", "zèbre");
    }
}
