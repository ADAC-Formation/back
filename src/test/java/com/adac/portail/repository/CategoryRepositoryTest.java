package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class CategoryRepositoryTest {

    private static final List<String> SEED_NOMS = List.of(
            "Estime de soi en travail social",
            "Méthodologie d'intervention sociale",
            "Difficultés budgétaires, surendettement",
            "Mieux-être au travail",
            "Spécial BCP",
            "Formation en intra");

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void sixSeedCategoriesArePresentWithExactNames() {
        List<String> noms = categoryRepository.findAll().stream().map(Category::getNom).toList();

        assertThat(noms).containsAll(SEED_NOMS);
    }

    @Test
    void seedCategoriesAreActiveWithHexColor() {
        List<Category> seeded = categoryRepository.findAll().stream()
                .filter(category -> SEED_NOMS.contains(category.getNom()))
                .toList();

        assertThat(seeded).hasSize(SEED_NOMS.size());
        assertThat(seeded).allSatisfy(category -> {
            assertThat(category.isActive()).isTrue();
            assertThat(category.getCouleur()).matches("^#[0-9A-Fa-f]{6}$");
        });
    }

    @Test
    void existsByNomIgnoreCaseIsCaseInsensitive() {
        boolean exists = categoryRepository.existsByNomIgnoreCase("estime de soi en travail social");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByNomIgnoreCaseReturnsFalseForUnknownName() {
        boolean exists = categoryRepository.existsByNomIgnoreCase("Catégorie de test inexistante XYZ");

        assertThat(exists).isFalse();
    }

    @Test
    void existsByNomIgnoreCaseAndIdNotIsFalseWhenTheOnlyMatchIsTheCategoryItself() {
        Category category = categoryRepository.findAll().stream()
                .filter(c -> SEED_NOMS.contains(c.getNom()))
                .findFirst().orElseThrow();

        boolean exists = categoryRepository.existsByNomIgnoreCaseAndIdNot(
                category.getNom().toUpperCase(), category.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void existsByNomIgnoreCaseAndIdNotIsTrueWhenAnotherCategoryHasTheName() {
        Category category = categoryRepository.findAll().stream()
                .filter(c -> SEED_NOMS.contains(c.getNom()))
                .findFirst().orElseThrow();

        boolean exists = categoryRepository.existsByNomIgnoreCaseAndIdNot(
                category.getNom().toLowerCase(), category.getId() + 1_000_000L);

        assertThat(exists).isTrue();
    }

    @Test
    void findAllByIsActiveTrueExcludesADeactivatedCategory() {
        List<Category> activeCategories = categoryRepository.findAllByIsActiveTrue();
        int activeBefore = activeCategories.size();

        Category toDeactivate = activeCategories.get(0);
        toDeactivate.setActive(false);
        categoryRepository.save(toDeactivate);

        List<Category> active = categoryRepository.findAllByIsActiveTrue();

        assertThat(active).extracting(Category::getId).doesNotContain(toDeactivate.getId());
        assertThat(active).hasSize(activeBefore - 1);
    }
}
