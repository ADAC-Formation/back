package com.adac.portail.service;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.enums.DeadlineAnchor;
import com.adac.portail.exception.DocumentTypeAlreadyExistsException;
import com.adac.portail.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real database and real mapper: the unit tests mock both, so they cannot see the join table, the
 * case-insensitive unique index or the response built from the saved entity. Each test rolls back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DocumentTypeIntegrationTest {

    @Autowired
    private DocumentTypeService documentTypeService;

    @Autowired
    private CategoryRepository categoryRepository;

    private static DocumentTypeRequest request(String nom) {
        DocumentTypeRequest request = new DocumentTypeRequest();
        request.setNom(nom);
        request.setRequired(true);
        request.setSensitive(false);
        request.setAppliesToAllFormations(true);
        return request;
    }

    @Test
    void createBuildsTheResponseWithSortedCategoriesAndSplitFormats() {
        List<Category> categories = categoryRepository.findAll().subList(0, 2);
        DocumentTypeRequest request = request("Certificat médical (test intégration)");
        request.setAppliesToAllFormations(false);
        request.setCategoryIds(categories.stream().map(Category::getId).toList());
        request.setDeadlineDays(7);
        request.setDeadlineAnchor(DeadlineAnchor.BEFORE_START);
        request.setAllowedFormats(List.of("PDF", "jpg"));

        DocumentTypeResponse response = documentTypeService.createDocumentType(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getAllowedFormats()).containsExactly("pdf", "jpg");
        assertThat(response.getDeadlineDays()).isEqualTo(7);
        assertThat(response.getCategories()).extracting(CategoryResponse::getId)
                .containsExactlyInAnyOrderElementsOf(categories.stream().map(Category::getId).toList());
        assertThat(response.getCategories()).extracting(CategoryResponse::getNom)
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void aTypeForAllFormationsHasNoCategoriesAndNoFormatRestriction() {
        DocumentTypeResponse response = documentTypeService.createDocumentType(request("Pièce d'identité (test intégration)"));

        assertThat(response.getCategories()).isEmpty();
        assertThat(response.getAllowedFormats()).isEmpty();
        assertThat(response.getDeadlineDays()).isNull();
    }

    @Test
    void theSameNameWithAnotherCaseIsRefused() {
        documentTypeService.createDocumentType(request("Fiche d'inscription (test intégration)"));

        assertThatThrownBy(() -> documentTypeService.createDocumentType(request("FICHE D'INSCRIPTION (TEST INTÉGRATION)")))
                .isInstanceOf(DocumentTypeAlreadyExistsException.class);
    }

    @Test
    void updateKeepsItsOwnNameAndReplacesTheCategories() {
        Category category = categoryRepository.findAll().get(0);
        DocumentTypeRequest create = request("Évaluation à chaud (test intégration)");
        create.setAppliesToAllFormations(false);
        create.setCategoryIds(List.of(category.getId()));
        Long id = documentTypeService.createDocumentType(create).getId();

        DocumentTypeRequest update = request("Évaluation à chaud (test intégration)");
        update.setRequired(false);
        DocumentTypeResponse response = documentTypeService.updateDocumentType(id, update);

        assertThat(response.isRequired()).isFalse();
        assertThat(response.isAppliesToAllFormations()).isTrue();
        assertThat(response.getCategories()).isEmpty();
    }

    @Test
    void deactivatedTypesLeaveTheActiveListButStayInTheFullList() {
        Long id = documentTypeService.createDocumentType(request("CV à jour (test intégration)")).getId();

        documentTypeService.deactivateDocumentType(id);

        assertThat(documentTypeService.getDocumentTypes(true)).extracting(DocumentTypeResponse::getId).doesNotContain(id);
        List<DocumentTypeResponse> all = documentTypeService.getDocumentTypes(null);
        assertThat(all).extracting(DocumentTypeResponse::getId).contains(id);
        assertThat(all).filteredOn(type -> type.getId().equals(id)).allMatch(type -> !type.isActive());
        assertThat(all).extracting(DocumentTypeResponse::getNom)
                .isSortedAccordingTo(Comparator.comparing(String::toLowerCase));
    }
}
