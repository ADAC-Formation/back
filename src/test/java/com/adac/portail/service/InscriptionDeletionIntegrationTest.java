package com.adac.portail.service;

import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.mapper.InscriptionMapper;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.DocumentRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Import(InscriptionServiceImpl.class)
class InscriptionDeletionIntegrationTest {

    @Autowired
    private InscriptionService inscriptionService;

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

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private InscriptionMapper inscriptionMapper;

    @MockitoBean
    private FormationService formationService;

    @MockitoBean
    private ActivationService activationService;

    @MockitoBean
    private StorageService storageService;

    private User saveUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(role)
                .build());
    }

    @Test
    void unenrollingAStagiaireWithADocumentDeletesTheDocumentAndTheInscription() {
        User superAdmin = saveUser("inscr-delete-admin@adac.fr", Role.SUPER_ADMIN);
        User stagiaire = saveUser("inscr-delete-stagiaire@adac.fr", Role.STAGIAIRE);
        Formation formation = formationRepository.save(Formation.builder()
                .intitule("Formation à désinscrire")
                .dateDebut(LocalDate.of(2026, 3, 10))
                .dateFin(LocalDate.of(2026, 3, 12))
                .modalite(Modalite.PRESENTIEL)
                .status(FormationStatus.ACTIVE)
                .category(categoryRepository.findAll().get(0))
                .createdBy(superAdmin)
                .build());
        Inscription inscription = inscriptionRepository.save(Inscription.builder()
                .stagiaire(stagiaire)
                .formation(formation)
                .build());
        Document document = documentRepository.save(Document.builder()
                .fileName("certificat.pdf")
                .fileUrl("https://supabase.example/certificat.pdf")
                .storagePath("inscriptions/x/certificat.pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .uploadedBy(stagiaire)
                .inscription(inscription)
                .build());
        entityManager.flush();
        entityManager.clear();

        inscriptionService.deleteInscription(formation.getId(), stagiaire.getId());
        entityManager.flush();

        assertThat(inscriptionRepository.findById(inscription.getId())).isEmpty();
        assertThat(documentRepository.findById(document.getId())).isEmpty();
        verify(storageService).delete("inscriptions/x/certificat.pdf");
    }
}
