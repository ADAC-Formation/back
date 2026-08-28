package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class FormationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

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

        Formation formation = Formation.builder()
                .intitule("Formation SST")
                .dateDebut(LocalDate.of(2026, 3, 10))
                .dateFin(LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)
                .createdBy(creator)
                .build();
        formationRepository.save(formation);

        List<Formation> found = formationRepository.findByStatus(FormationStatus.ACTIVE);

        assertThat(found).extracting(Formation::getIntitule).contains("Formation SST");
    }
}
