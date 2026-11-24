package com.adac.portail.service;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real JPA session, outside any transaction: {@code uploadDocument} is deliberately not transactional
 * (Supabase upload, then save with compensation), so the formation reached through an inscription is a
 * lazy proxy. The unit tests mock the repositories and cannot see that the proxy fails to load.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DocumentUploadIntegrationTest {

    private static final String EMAIL_PATTERN = "doc-upload-%@adac.fr";
    private static final String CATEGORY_NAME = "doc-upload-category";

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FormationRepository formationRepository;

    @Autowired
    private InscriptionRepository inscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private JavaMailSender mailSender;

    private User chargee;
    private User stagiaire;

    @BeforeEach
    void setUp() {
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://storage.test/file.pdf");
        deleteTestData();
        chargee = saveUser("doc-upload-chargee@adac.fr", Role.SUPER_ADMIN);
        stagiaire = saveUser("doc-upload-stagiaire@adac.fr", Role.STAGIAIRE);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void aStagiaireUploadsADocumentOnTheirOwnInscription() {
        Inscription inscription = saveInscription(FormationStatus.ACTIVE);

        DocumentResponse response = documentService.uploadDocument(
                pdf(), null, inscription.getId(), new AdacUserDetails(stagiaire));

        assertThat(response.getFileName()).isEqualTo("attestation.pdf");
    }

    @Test
    void theChargeeUploadsADocumentForAStagiaire() {
        Inscription inscription = saveInscription(FormationStatus.ACTIVE);

        DocumentResponse response = documentService.uploadDocument(
                pdf(), null, inscription.getId(), new AdacUserDetails(chargee));

        assertThat(response.getFileName()).isEqualTo("attestation.pdf");
    }

    @Test
    void anUploadOnAnInscriptionOfAnArchivedFormationIsRefused() {
        Inscription inscription = saveInscription(FormationStatus.ARCHIVED);

        assertThatThrownBy(() -> documentService.uploadDocument(
                pdf(), null, inscription.getId(), new AdacUserDetails(stagiaire)))
                .isInstanceOf(ConflictException.class);
    }

    private Inscription saveInscription(FormationStatus status) {
        Category category = categoryRepository.save(Category.builder()
                .nom(CATEGORY_NAME)
                .couleur("#123456")
                .build());
        Formation formation = formationRepository.save(Formation.builder()
                .intitule("doc-upload-formation")
                .dateDebut(LocalDate.now())
                .dateFin(LocalDate.now().plusDays(1))
                .modalite(Modalite.PRESENTIEL)
                .status(status)
                .category(category)
                .createdBy(chargee)
                .build());
        return inscriptionRepository.save(Inscription.builder()
                .stagiaire(stagiaire)
                .formation(formation)
                .build());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "attestation.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
    }

    private User saveUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("irrelevant")
                .nom("Upload")
                .prenom("Test")
                .role(role)
                .isActive(true)
                .build());
    }

    private void deleteTestData() {
        String testUsers = "select id from users where email like '" + EMAIL_PATTERN + "'";
        jdbcTemplate.update("delete from notifications where recipient_id in (" + testUsers + ")");
        jdbcTemplate.update("delete from documents where uploaded_by in (" + testUsers + ")");
        jdbcTemplate.update("delete from inscriptions where stagiaire_id in (" + testUsers + ")");
        jdbcTemplate.update("delete from formations where created_by in (" + testUsers + ")");
        jdbcTemplate.update("delete from categories where nom = '" + CATEGORY_NAME + "'");
        jdbcTemplate.update("delete from users where email like '" + EMAIL_PATTERN + "'");
    }
}
