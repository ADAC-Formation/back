package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class FormationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Test
    void savedActiveFormationIsFoundByStatus() {
        User creator = userRepository.save(User.builder()
                .email("formation-repo-test@adac.fr")
                .passwordHash("hashed")
                .nom("Admin")
                .prenom("Super")
                .role(Role.SUPER_ADMIN)
                .build());
        // TICKET-046: category_id is NOT NULL since V2 — any of the 6 seeded categories works,
        // this test isn't about which one.
        Category category = categoryRepository.findAll().get(0);

        Formation formation = Formation.builder()
                .intitule("Formation SST")
                .dateDebut(LocalDate.of(2026, 3, 10))
                .dateFin(LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)
                .category(category)
                .createdBy(creator)
                .build();
        formationRepository.save(formation);

        List<Formation> found = formationRepository.findByStatus(FormationStatus.ACTIVE);

        assertThat(found).extracting(Formation::getIntitule).contains("Formation SST");
    }

    @Test
    void savingFormationWithoutCategoryThrowsDataIntegrityViolationException() {
        // TICKET-046 acceptance criterion: category_id NOT NULL + FK, enforced at the DB level —
        // no @NotNull/Bean Validation involved, so this must reach the actual INSERT.
        User creator = userRepository.save(User.builder()
                .email("formation-no-category-test@adac.fr")
                .passwordHash("hashed")
                .nom("Admin")
                .prenom("Super")
                .role(Role.SUPER_ADMIN)
                .build());

        Formation formation = Formation.builder()
                .intitule("Formation sans catégorie")
                .dateDebut(LocalDate.of(2026, 3, 10))
                .dateFin(LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)
                .createdBy(creator)
                .build();

        // saveAndFlush, not save: this only reaches the DB constraint today because
        // GenerationType.IDENTITY forces an immediate INSERT — an incidental detail (review,
        // TICKET-046). saveAndFlush makes the round trip explicit, matching
        // DocumentRepositoryTest's equivalent constraint tests.
        assertThatThrownBy(() -> formationRepository.saveAndFlush(formation))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
