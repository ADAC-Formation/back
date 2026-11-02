package com.adac.portail.repository;

import com.adac.portail.entity.Document;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class InscriptionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InscriptionRepository inscriptionRepository;

    @Autowired
    private DocumentRepository documentRepository;

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

                .category(categoryRepository.findAll().get(0))
                .formateur(formateur)
                .createdBy(creator)
                .build());
    }

    @Test
    void findAllByFormationReturnsEveryInscriptionForThatFormation() {
        User superAdmin = saveUser("inscr-repo-admin7@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        Formation otherFormation = saveFormation(superAdmin, null);
        User stagiaire1 = saveUser("inscr-repo-eg-1@adac.fr", Role.STAGIAIRE);
        User stagiaire2 = saveUser("inscr-repo-eg-2@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire1).formation(formation).build());
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire2).formation(formation).build());
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire1).formation(otherFormation).build());

        List<Inscription> found = inscriptionRepository.findAllByFormation(formation);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(i -> i.getStagiaire().getEmail())
                .containsExactlyInAnyOrder("inscr-repo-eg-1@adac.fr", "inscr-repo-eg-2@adac.fr");
    }

    @Test
    void findStagiairesByFormationReturnsFullyUsableUsersNotLazyProxies() {
        User superAdmin = saveUser("inscr-repo-admin@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        User stagiaire = saveUser("inscr-repo-stagiaire@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation).build());

        List<User> found = inscriptionRepository.findStagiairesByFormation(formation);

        assertThat(found).hasSize(1);

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

    @Test
    void findFormationsByStagiaireReturnsOnlyTheFormationsTheyAreEnrolledIn() {
        User superAdmin = saveUser("inscr-repo-admin5@adac.fr", Role.SUPER_ADMIN);
        Formation enrolled = saveFormation(superAdmin, null);
        Formation notEnrolled = saveFormation(superAdmin, null);
        User stagiaire = saveUser("inscr-repo-formations-stagiaire@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(enrolled).build());

        List<Formation> found = inscriptionRepository.findFormationsByStagiaire(stagiaire);

        assertThat(found).extracting(Formation::getId)
                .containsExactly(enrolled.getId())
                .doesNotContain(notEnrolled.getId());
    }

    @Test
    void existsByStagiaireAndFormationIsFalseWhenNotEnrolled() {
        User superAdmin = saveUser("inscr-repo-admin6@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        User enrolledStagiaire = saveUser("inscr-repo-enrolled@adac.fr", Role.STAGIAIRE);
        User otherStagiaire = saveUser("inscr-repo-not-enrolled@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(enrolledStagiaire).formation(formation).build());

        assertThat(inscriptionRepository.existsByStagiaireAndFormation(enrolledStagiaire, formation)).isTrue();
        assertThat(inscriptionRepository.existsByStagiaireAndFormation(otherStagiaire, formation)).isFalse();
    }

    @Test
    void findStagiairesWithNoDocumentsExcludesThoseWithAnInscriptionTargetedDocument() {
        User superAdmin = saveUser("inscr-repo-admin8@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        User withDoc = saveUser("inscr-repo-with-doc@adac.fr", Role.STAGIAIRE);
        User withoutDoc = saveUser("inscr-repo-without-doc@adac.fr", Role.STAGIAIRE);
        Inscription inscriptionWithDoc =
                inscriptionRepository.save(Inscription.builder().stagiaire(withDoc).formation(formation).build());
        inscriptionRepository.save(Inscription.builder().stagiaire(withoutDoc).formation(formation).build());
        documentRepository.save(Document.builder()
                .fileName("attestation.pdf")
                .fileUrl("https://supabase.example/attestation.pdf")
                .fileSize(100L)
                .mimeType("application/pdf")
                .uploadedBy(superAdmin)
                .inscription(inscriptionWithDoc)
                .storagePath("documents/test-fixture.pdf")
                .build());

        List<User> found = inscriptionRepository.findStagiairesWithNoDocuments();

        assertThat(found).extracting(User::getEmail)
                .contains("inscr-repo-without-doc@adac.fr")
                .doesNotContain("inscr-repo-with-doc@adac.fr");
    }

    @Test
    void findStagiairesWithNoDocumentsIgnoresFormationWideDocuments() {
        User superAdmin = saveUser("inscr-repo-admin9@adac.fr", Role.SUPER_ADMIN);
        Formation formation = saveFormation(superAdmin, null);
        User stagiaire = saveUser("inscr-repo-formation-doc-only@adac.fr", Role.STAGIAIRE);
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formation).build());
        documentRepository.save(Document.builder()
                .fileName("programme.pdf")
                .fileUrl("https://supabase.example/programme.pdf")
                .fileSize(100L)
                .mimeType("application/pdf")
                .uploadedBy(superAdmin)
                .formation(formation)
                .storagePath("documents/test-fixture.pdf")
                .build());

        assertThat(inscriptionRepository.findStagiairesWithNoDocuments())
                .extracting(User::getEmail)
                .contains("inscr-repo-formation-doc-only@adac.fr");
    }

    @Test
    void findStagiairesWithNoDocumentsIsGlobalAcrossAllOfAStagiairesFormationsNotPerFormation() {
        User superAdmin = saveUser("inscr-repo-admin10@adac.fr", Role.SUPER_ADMIN);
        Formation formationA = saveFormation(superAdmin, null);
        Formation formationB = saveFormation(superAdmin, null);
        User stagiaire = saveUser("inscr-repo-multi-formation@adac.fr", Role.STAGIAIRE);
        Inscription inscriptionA =
                inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formationA).build());
        inscriptionRepository.save(Inscription.builder().stagiaire(stagiaire).formation(formationB).build());
        documentRepository.save(Document.builder()
                .fileName("attestation.pdf")
                .fileUrl("https://supabase.example/attestation.pdf")
                .fileSize(100L)
                .mimeType("application/pdf")
                .uploadedBy(superAdmin)
                .inscription(inscriptionA)
                .storagePath("documents/test-fixture.pdf")
                .build());

        assertThat(inscriptionRepository.findStagiairesWithNoDocuments())
                .extracting(User::getEmail)
                .doesNotContain("inscr-repo-multi-formation@adac.fr");
    }

    @Test
    void findStagiairesWithNoDocumentsExcludesStagiairesWithNoInscriptionAtAll() {
        saveUser("inscr-repo-not-enrolled-at-all@adac.fr", Role.STAGIAIRE);

        assertThat(inscriptionRepository.findStagiairesWithNoDocuments())
                .extracting(User::getEmail)
                .doesNotContain("inscr-repo-not-enrolled-at-all@adac.fr");
    }
}
