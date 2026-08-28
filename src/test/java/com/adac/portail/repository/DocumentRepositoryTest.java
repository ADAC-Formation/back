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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class DocumentRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private InscriptionRepository inscriptionRepository;

    @Test
    void documentLinkedToFormationHasNoInscription() {
        User uploader = userRepository.save(User.builder()
                .email("document-repo-test@adac.fr")
                .passwordHash("hashed")
                .nom("Admin")
                .prenom("Super")
                .role(Role.SUPER_ADMIN)
                .build());

        Formation formation = formationRepository.save(Formation.builder()
                .intitule("Formation avec document")
                .dateDebut(LocalDate.of(2026, 4, 1))
                .dateFin(LocalDate.of(2026, 4, 2))
                .modalite(Modalite.VISIO)
                .status(FormationStatus.ACTIVE)
                .createdBy(uploader)
                .build());

        Document document = Document.builder()
                .fileName("programme.pdf")
                .fileUrl("https://supabase.example/programme.pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .uploadedBy(uploader)
                .formation(formation)
                .build();
        Document saved = documentRepository.save(document);

        Document found = documentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFormation().getId()).isEqualTo(formation.getId());
        assertThat(found.getInscription()).isNull();
    }

    @Test
    void documentWithNeitherFormationNorInscriptionViolatesCheckConstraint() {
        User uploader = userRepository.save(User.builder()
                .email("document-repo-test-neither@adac.fr")
                .passwordHash("hashed")
                .nom("Admin")
                .prenom("Super")
                .role(Role.SUPER_ADMIN)
                .build());

        Document document = Document.builder()
                .fileName("orphan.pdf")
                .fileUrl("https://supabase.example/orphan.pdf")
                .fileSize(512L)
                .mimeType("application/pdf")
                .uploadedBy(uploader)
                .build();

        assertThatThrownBy(() -> documentRepository.saveAndFlush(document))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void documentWithBothFormationAndInscriptionViolatesCheckConstraint() {
        User uploader = userRepository.save(User.builder()
                .email("document-repo-test-both@adac.fr")
                .passwordHash("hashed")
                .nom("Admin")
                .prenom("Super")
                .role(Role.SUPER_ADMIN)
                .build());

        Formation formation = formationRepository.save(Formation.builder()
                .intitule("Formation pour les deux FK")
                .dateDebut(LocalDate.of(2026, 5, 1))
                .dateFin(LocalDate.of(2026, 5, 2))
                .modalite(Modalite.VISIO)
                .status(FormationStatus.ACTIVE)
                .createdBy(uploader)
                .build());

        Inscription inscription = inscriptionRepository.save(Inscription.builder()
                .stagiaire(uploader)
                .formation(formation)
                .build());

        Document document = Document.builder()
                .fileName("both.pdf")
                .fileUrl("https://supabase.example/both.pdf")
                .fileSize(512L)
                .mimeType("application/pdf")
                .uploadedBy(uploader)
                .formation(formation)
                .inscription(inscription)
                .build();

        assertThatThrownBy(() -> documentRepository.saveAndFlush(document))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
