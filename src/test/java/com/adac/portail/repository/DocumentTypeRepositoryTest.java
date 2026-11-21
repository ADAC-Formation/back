package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import com.adac.portail.entity.DocumentType;
import com.adac.portail.entity.enums.DeadlineAnchor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class DocumentTypeRepositoryTest {

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private DocumentType.DocumentTypeBuilder aType(String nom) {
        return DocumentType.builder().nom(nom).appliesToAll(true);
    }

    @Test
    void savesATypeWithItsCategoriesAndReadsItBack() {
        List<Category> categories = categoryRepository.findAll().subList(0, 2);
        DocumentType saved = documentTypeRepository.saveAndFlush(aType("Certificat médical de test")
                .aide("Moins de 3 mois")
                .sensitive(true)
                .appliesToAll(false)
                .categories(Set.copyOf(categories))
                .deadlineDays(7)
                .deadlineAnchor(DeadlineAnchor.BEFORE_START)
                .allowedFormats("pdf,jpg")
                .build());

        DocumentType read = documentTypeRepository.findById(saved.getId()).orElseThrow();

        assertThat(read.getNom()).isEqualTo("Certificat médical de test");
        assertThat(read.isSensitive()).isTrue();
        assertThat(read.isActive()).isTrue();
        assertThat(read.getCategories()).containsExactlyInAnyOrderElementsOf(categories);
        assertThat(read.getDeadlineDays()).isEqualTo(7);
        assertThat(read.getDeadlineAnchor()).isEqualTo(DeadlineAnchor.BEFORE_START);
        assertThat(read.getAllowedFormats()).isEqualTo("pdf,jpg");
        assertThat(read.getCreatedAt()).isNotNull();
    }

    @Test
    void refusesTwoTypesWhoseNamesDifferOnlyByCase() {
        documentTypeRepository.saveAndFlush(aType("Pièce d'identité de test").build());

        assertThatThrownBy(() -> documentTypeRepository.saveAndFlush(aType("PIÈCE D'IDENTITÉ DE TEST").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesADeadlineWithoutAnAnchor() {
        assertThatThrownBy(() -> documentTypeRepository.saveAndFlush(aType("Sans repère").deadlineDays(5).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAnAnchorWithoutDays() {
        assertThatThrownBy(() -> documentTypeRepository.saveAndFlush(
                aType("Sans jours").deadlineAnchor(DeadlineAnchor.AFTER_END).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesDeadlineDaysOutsideZeroTo365() {
        assertThatThrownBy(() -> documentTypeRepository.saveAndFlush(
                aType("Trop long").deadlineDays(366).deadlineAnchor(DeadlineAnchor.AFTER_END).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsADeadlineOfZeroDays() {
        DocumentType saved = documentTypeRepository.saveAndFlush(
                aType("Le jour même").deadlineDays(0).deadlineAnchor(DeadlineAnchor.BEFORE_START).build());

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void existsByNomIgnoreCaseIsCaseInsensitiveAndIdNotExcludesItself() {
        DocumentType saved = documentTypeRepository.saveAndFlush(aType("Fiche d'inscription de test").build());

        assertThat(documentTypeRepository.existsByNomIgnoreCase("fiche d'inscription de TEST")).isTrue();
        assertThat(documentTypeRepository.existsByNomIgnoreCaseAndIdNot("fiche d'inscription de test", saved.getId())).isFalse();
        assertThat(documentTypeRepository.existsByNomIgnoreCaseAndIdNot("fiche d'inscription de test", saved.getId() + 1)).isTrue();
        assertThat(documentTypeRepository.existsByNomIgnoreCase("Nom jamais utilisé XYZ")).isFalse();
    }

    @Test
    void findAllByActiveTrueSkipsDeactivatedTypes() {
        documentTypeRepository.saveAndFlush(aType("Actif de test").build());
        documentTypeRepository.saveAndFlush(aType("Désactivé de test").active(false).build());

        List<String> actifs = documentTypeRepository.findAllByActiveTrue().stream().map(DocumentType::getNom).toList();

        assertThat(actifs).contains("Actif de test").doesNotContain("Désactivé de test");
    }
}
