package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
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

/**
 * Against a real Postgres/Hibernate session (unlike {@code UserServiceImplTest}, which mocks
 * this repository) — proves the JPQL projects straight to a fully-loaded {@code User} row per
 * query, rather than returning {@code Inscription.stagiaire} (a LAZY association) as an
 * uninitialized proxy.
 *
 * <p><b>What this class does NOT prove</b> (branch-wide review correction): {@code @DataJpaTest}
 * is {@code @Transactional} by default, so the persistence context stays open for the whole test
 * method and the {@code User} returned here is often the very same managed instance saved earlier
 * in it (first-level cache) — a LAZY proxy would happily initialize under those conditions too,
 * and the assertions below would pass just as well against the old, buggy
 * {@code findAllByFormation(...).stream().map(Inscription::getStagiaire)} implementation that
 * originally threw {@code LazyInitializationException} in production (this app runs with
 * {@code spring.jpa.open-in-view: false}, so there the persistence context is long closed by the
 * time the controller serializes the response). That regression is only actually reproducible —
 * and only actually guarded — by a real HTTP round trip through the full filter chain with no
 * open transaction left by the time the response body is built; see
 * {@code JwtAuthenticationIntegrationTest#getStagiairesThroughRealFilterChainReturnsFullyUsableUsersNotLazyProxies()}.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class InscriptionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Autowired
    private InscriptionRepository inscriptionRepository;

    private User saveUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(role)
                .build());
    }

    private Formation saveFormation(User creator, User formateur) {
        return formationRepository.save(Formation.builder()
                .intitule("Formation SST")
                .dateDebut(LocalDate.of(2026, 3, 10))
                .dateFin(LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)
                .formateur(formateur)
                .createdBy(creator)
                .build());
    }

    @Test
    void findStagiairesByFormationReturnsFullyUsableUsersNotLazyProxies() {
        User superAdmin = saveUser("inscr-repo-admin@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        User stagiaire = saveUser("inscr-repo-stagiaire@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation).build());

        List<User> found = inscriptionRepository.findStagiairesByFormation(formation);

        assertThat(found).hasSize(1);
        // Touching a field the LAZY-proxy version of this query left uninitialized — this is
        // exactly what threw LazyInitializationException before the TICKET-019 review fix.
        assertThat(found.get(0).isActive()).isTrue();
        assertThat(found.get(0).getEmail()).isEqualTo("inscr-repo-stagiaire@adac.fr");
    }

    @Test
    void findStagiairesByFormateurReturnsDistinctStagiairesAcrossAllTheirFormations() {
        User superAdmin = saveUser("inscr-repo-admin2@adac.fr", Role.SUPER_ADMIN);
        User formateur = saveUser("inscr-repo-formateur@adac.fr", Role.ADMIN);
        Formation formation1 = saveFormation(superAdmin, formateur);
        Formation formation2 = saveFormation(superAdmin, formateur);
        User stagiaire = saveUser("inscr-repo-multi-stagiaire@adac.fr", Role.STAGIAIRE);
        // Enrolled in both of this formateur's formations — must be returned once, not twice.
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation1).build());
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation2).build());

        List<User> found = inscriptionRepository.findStagiairesByFormateur(formateur);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEmail()).isEqualTo("inscr-repo-multi-stagiaire@adac.fr");
    }

    @Test
    void findStagiairesByFormateurDoesNotReturnStagiairesOfAnotherFormateur() {
        User superAdmin = saveUser("inscr-repo-admin3@adac.fr", Role.SUPER_ADMIN);
        User formateurA = saveUser("inscr-repo-formateur-a@adac.fr", Role.ADMIN);
        User formateurB = saveUser("inscr-repo-formateur-b@adac.fr", Role.ADMIN);
        Formation formationOfB = saveFormation(superAdmin, formateurB);
        User stagiaire = saveUser("inscr-repo-foreign-stagiaire@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formationOfB).build());

        assertThat(inscriptionRepository.findStagiairesByFormateur(formateurA)).isEmpty();
    }

    @Test
    void existsByStagiaireAndFormationFormateurIsTrueOnlyForTheOwningFormateur() {
        User superAdmin = saveUser("inscr-repo-admin4@adac.fr", Role.SUPER_ADMIN);
        User formateur = saveUser("inscr-repo-formateur-owner@adac.fr", Role.ADMIN);
        User otherFormateur = saveUser("inscr-repo-formateur-other@adac.fr", Role.ADMIN);
        Formation formation = saveFormation(superAdmin, formateur);
        User stagiaire = saveUser("inscr-repo-ownership-stagiaire@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation).build());

        assertThat(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, formateur)).isTrue();
        assertThat(inscriptionRepository.existsByStagiaireAndFormation_Formateur(stagiaire, otherFormateur)).isFalse();
    }
}
