package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-046 — V2__add_categories.sql seeds 6 categories; this exercises them via the real
 * migrated schema (AutoConfigureTestDatabase.Replace.NONE, like FormationRepositoryTest), not an
 * in-memory DB, so Flyway actually runs V2 for this test too.
 *
 * <p>Assertions below are deliberately state-relative (never "the DB has exactly N rows" /
 * "every row is active"): this runs against the real shared dev Postgres, not an isolated
 * database, and @DataJpaTest's per-test rollback doesn't undo rows another tool (Swagger,
 * TICKET-047's own CRUD once it lands) committed outside a test — asserting on the full table's
 * global state would make this class flaky for reasons unrelated to the code under test
 * (review, TICKET-046).</p>
 */
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
    void findAllByIsActiveTrueExcludesADeactivatedCategory() {
        List<Category> activeCategories = categoryRepository.findAllByIsActiveTrue();
        int activeBefore = activeCategories.size();
        // Must pick a category that's actually active right now (not just findAll().get(0)) —
        // this runs against the shared dev DB, so an unrelated already-deactivated row would make
        // this a no-op and the size assertion below flaky.
        Category toDeactivate = activeCategories.get(0);
        toDeactivate.setActive(false);
        categoryRepository.save(toDeactivate);

        List<Category> active = categoryRepository.findAllByIsActiveTrue();

        assertThat(active).extracting(Category::getId).doesNotContain(toDeactivate.getId());
        assertThat(active).hasSize(activeBefore - 1);
    }
}
