package com.adac.portail.service;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.mapper.DocumentMapper;
import com.adac.portail.repository.DocumentRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.utils.FileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TICKET-026 — see docs/tickets/TICKET-026.md § Write tests first (Test 1, 2, 4, 5 here). */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private InscriptionRepository inscriptionRepository;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private static User user(long id, Role role) {
        return User.builder().id(id).nom("Doe").prenom("Jane").role(role).isActive(true).build();
    }

    private static AdacUserDetails principal(User user) {
        return new AdacUserDetails(user);
    }

    private static MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "programme.pdf", "application/pdf", "contenu".getBytes());
    }

    private void stubCanonicalMimeType() {
        when(fileValidator.canonicalMimeType(anyString())).thenReturn("application/pdf");
    }

    // --- XOR validation (Test 2, and the "neither" AC) -----------------------------------------

    @Test
    void uploadWithBothFormationIdAndInscriptionIdReturnsBadRequest() {
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), 1L, 2L, caller))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(storageService, documentRepository);
    }

    @Test
    void uploadWithNeitherFormationIdNorInscriptionIdReturnsBadRequest() {
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), null, null, caller))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(storageService, documentRepository);
    }

    // --- Role matrix on formationId ---------------------------------------------------------

    @Test
    void uploadOnUnknownFormationReturnsNotFound() {
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));
        when(formationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), 404L, null, caller))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Ticket Test 4: ADMIN upload on another formateur's formation -> 403.
    @Test
    void uploadByAdminOnAnotherFormateursFormationReturnsForbidden() {
        User otherFormateur = user(2L, Role.ADMIN);
        Formation formation = Formation.builder().id(1L).formateur(otherFormateur).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        AdacUserDetails caller = principal(user(3L, Role.ADMIN));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), 1L, null, caller))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void uploadByAdminOnOwnFormationSucceeds() {
        User formateur = user(2L, Role.ADMIN);
        Formation formation = Formation.builder().id(1L).formateur(formateur).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/formations/1/x.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(9L).build());
        when(inscriptionRepository.findStagiairesByFormation(formation)).thenReturn(List.of());

        DocumentResponse response = documentService.uploadDocument(pdfFile(), 1L, null, principal(formateur));

        assertThat(response.getId()).isEqualTo(9L);
    }

    @Test
    void uploadWithFormationIdByStagiaireReturnsForbidden() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        AdacUserDetails caller = principal(user(3L, Role.STAGIAIRE));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), 1L, null, caller))
                .isInstanceOf(UnauthorizedException.class);
    }

    // --- Role matrix on inscriptionId --------------------------------------------------------

    @Test
    void uploadWithInscriptionIdByAdminReturnsForbidden() {
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        AdacUserDetails caller = principal(user(2L, Role.ADMIN));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), null, 5L, caller))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadOnSomeoneElsesInscriptionByStagiaireReturnsForbidden() {
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        AdacUserDetails caller = principal(user(8L, Role.STAGIAIRE));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), null, 5L, caller))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadOnOwnInscriptionByStagiaireSucceeds() {
        User stagiaire = user(7L, Role.STAGIAIRE);
        Inscription inscription = Inscription.builder().id(5L).stagiaire(stagiaire)
                .formation(Formation.builder().id(1L).build()).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/inscriptions/5/x.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(11L).build());

        DocumentResponse response = documentService.uploadDocument(pdfFile(), null, 5L, principal(stagiaire));

        assertThat(response.getId()).isEqualTo(11L);
    }

    // --- Ticket Test 1 + 5: valid upload -> 201-equivalent + storage/notify wiring -------------

    @Test
    void uploadOnOwnFormationBySuperAdminCallsStorageSavesUrlAndNotifiesEnrolledStagiaires() {
        User superAdmin = user(1L, Role.SUPER_ADMIN);
        Formation formation = Formation.builder().id(1L).build();
        User stagiaire1 = user(10L, Role.STAGIAIRE);
        User stagiaire2 = user(11L, Role.STAGIAIRE);
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/formations/1/programme.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(1L).build());
        when(inscriptionRepository.findStagiairesByFormation(formation)).thenReturn(List.of(stagiaire1, stagiaire2));

        documentService.uploadDocument(pdfFile(), 1L, null, principal(superAdmin));

        verify(fileValidator).validate(any());
        verify(storageService).upload(any(), anyString(), eq("application/pdf"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getFileUrl()).isEqualTo("https://supabase/formations/1/programme.pdf");
        assertThat(captor.getValue().getFileName()).isEqualTo("programme.pdf");
        assertThat(captor.getValue().getMimeType()).isEqualTo("application/pdf");
        assertThat(captor.getValue().getStoragePath())
                .matches(Pattern.compile("^formations/1/[0-9a-f-]{36}-programme\\.pdf$"));

        verify(notificationService).notify(eq(10L), eq(NotificationType.DOCUMENT_UPLOADED), anyString(),
                eq(EntityType.FORMATION), eq(1L));
        verify(notificationService).notify(eq(11L), eq(NotificationType.DOCUMENT_UPLOADED), anyString(),
                eq(EntityType.FORMATION), eq(1L));
        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void uploadOnInscriptionNotifiesOnlyThatStagiaire() {
        User stagiaire = user(7L, Role.STAGIAIRE);
        User otherUploader = user(99L, Role.SUPER_ADMIN);
        Formation formation = Formation.builder().id(1L).build();
        Inscription inscription = Inscription.builder().id(5L).stagiaire(stagiaire).formation(formation).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/inscriptions/5/x.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(2L).build());

        // Uploaded by SUPER_ADMIN on behalf of the stagiaire — notifying makes sense here.
        documentService.uploadDocument(pdfFile(), null, 5L, principal(otherUploader));

        verify(notificationService).notify(eq(7L), eq(NotificationType.DOCUMENT_UPLOADED), anyString(),
                eq(EntityType.FORMATION), eq(1L));
        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any());
    }

    // Branch-wide review: a stagiaire uploading to their own inscription shouldn't be notified
    // about the document they just deposited.
    @Test
    void uploadOnOwnInscriptionByStagiaireDoesNotSelfNotify() {
        User stagiaire = user(7L, Role.STAGIAIRE);
        Formation formation = Formation.builder().id(1L).build();
        Inscription inscription = Inscription.builder().id(5L).stagiaire(stagiaire).formation(formation).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/inscriptions/5/x.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(2L).build());

        documentService.uploadDocument(pdfFile(), null, 5L, principal(stagiaire));

        verifyNoInteractions(notificationService);
    }

    // Branch-wide review: a DB failure after a successful Supabase upload must not orphan the
    // object — the upload should be rolled back too.
    @Test
    void uploadDeletesStorageObjectWhenPersistenceFails() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/formations/1/x.pdf");
        when(documentRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> documentService.uploadDocument(pdfFile(), 1L, null, principal(user(1L, Role.SUPER_ADMIN))))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).delete(pathCaptor.capture());
        assertThat(pathCaptor.getValue()).matches(Pattern.compile("^formations/1/[0-9a-f-]{36}-programme\\.pdf$"));
    }

    // Branch-wide review: a notification failure is a secondary effect and must not fail the
    // upload — the document is already durably saved by this point.
    @Test
    void uploadSucceedsEvenWhenNotificationFails() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        stubCanonicalMimeType();
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("https://supabase/formations/1/x.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(1L).build());
        when(inscriptionRepository.findStagiairesByFormation(formation)).thenReturn(List.of(user(10L, Role.STAGIAIRE)));
        doThrow(new ResourceNotFoundException("Utilisateur introuvable"))
                .when(notificationService).notify(any(), any(), any(), any(), any());

        DocumentResponse response = documentService.uploadDocument(pdfFile(), 1L, null, principal(user(1L, Role.SUPER_ADMIN)));

        assertThat(response.getId()).isEqualTo(1L);
        verify(storageService, never()).delete(anyString());
    }

    // --- getDocuments -------------------------------------------------------------------------

    @Test
    void getDocumentsWithBothParamsReturnsBadRequest() {
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.getDocuments(1L, 2L, caller)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getDocumentsWithNeitherParamReturnsBadRequest() {
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.getDocuments(null, null, caller)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getDocumentsByFormationIdForStagiaireNotEnrolledReturnsForbidden() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        User stagiaire = user(7L, Role.STAGIAIRE);
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(false);

        assertThatThrownBy(() -> documentService.getDocuments(1L, null, principal(stagiaire)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getDocumentsByFormationIdForEnrolledStagiaireReturnsList() {
        Formation formation = Formation.builder().id(1L).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        User stagiaire = user(7L, Role.STAGIAIRE);
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(true);
        when(documentRepository.findAllByFormation(formation)).thenReturn(List.of(new Document()));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(1L).build());

        List<DocumentResponse> result = documentService.getDocuments(1L, null, principal(stagiaire));

        assertThat(result).hasSize(1);
    }

    // Branch-wide review (security): non-owning ADMIN gets 404, not 403 — an existence oracle,
    // same convention as FormationServiceImpl.getFormationById.
    @Test
    void getDocumentsByFormationIdForAdminNotOwnReturnsNotFound() {
        Formation formation = Formation.builder().id(1L).formateur(user(2L, Role.ADMIN)).build();
        when(formationRepository.findById(1L)).thenReturn(Optional.of(formation));
        AdacUserDetails caller = principal(user(3L, Role.ADMIN));

        assertThatThrownBy(() -> documentService.getDocuments(1L, null, caller)).isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getDocuments by inscriptionId (branch-wide review: previously untested) ---------------

    @Test
    void getDocumentsByInscriptionIdForOwningStagiaireReturnsList() {
        User stagiaire = user(7L, Role.STAGIAIRE);
        Inscription inscription = Inscription.builder().id(5L).stagiaire(stagiaire).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        when(documentRepository.findAllByInscription(inscription)).thenReturn(List.of(new Document()));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(1L).build());

        List<DocumentResponse> result = documentService.getDocuments(null, 5L, principal(stagiaire));

        assertThat(result).hasSize(1);
    }

    @Test
    void getDocumentsByInscriptionIdForAnotherStagiaireReturnsForbidden() {
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        AdacUserDetails caller = principal(user(8L, Role.STAGIAIRE));

        assertThatThrownBy(() -> documentService.getDocuments(null, 5L, caller)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getDocumentsByInscriptionIdForOwningFormateurReturnsList() {
        User formateur = user(2L, Role.ADMIN);
        Formation formation = Formation.builder().id(1L).formateur(formateur).build();
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).formation(formation).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        when(documentRepository.findAllByInscription(inscription)).thenReturn(List.of(new Document()));
        when(documentMapper.toResponse(any())).thenReturn(DocumentResponse.builder().id(1L).build());

        List<DocumentResponse> result = documentService.getDocuments(null, 5L, principal(formateur));

        assertThat(result).hasSize(1);
    }

    @Test
    void getDocumentsByInscriptionIdForNonOwningFormateurReturnsNotFound() {
        Formation formation = Formation.builder().id(1L).formateur(user(2L, Role.ADMIN)).build();
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).formation(formation).build();
        when(inscriptionRepository.findById(5L)).thenReturn(Optional.of(inscription));
        AdacUserDetails caller = principal(user(3L, Role.ADMIN));

        assertThatThrownBy(() -> documentService.getDocuments(null, 5L, caller)).isInstanceOf(ResourceNotFoundException.class);
    }

    // --- downloadDocument -----------------------------------------------------------------------

    @Test
    void downloadUnknownDocumentReturnsNotFound() {
        when(documentRepository.findById(404L)).thenReturn(Optional.empty());
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.downloadDocument(404L, caller)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadFormationDocumentByNotEnrolledStagiaireReturnsForbidden() {
        Formation formation = Formation.builder().id(1L).build();
        Document document = Document.builder().id(9L).formation(formation).storagePath("formations/1/x.pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        User stagiaire = user(7L, Role.STAGIAIRE);
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(false);

        assertThatThrownBy(() -> documentService.downloadDocument(9L, principal(stagiaire)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void downloadFormationDocumentByEnrolledStagiaireReturnsBytes() {
        Formation formation = Formation.builder().id(1L).build();
        Document document = Document.builder().id(9L).formation(formation).storagePath("formations/1/x.pdf")
                .fileName("x.pdf").mimeType("application/pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        User stagiaire = user(7L, Role.STAGIAIRE);
        when(inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)).thenReturn(true);
        when(storageService.download("formations/1/x.pdf")).thenReturn("bytes".getBytes());

        DownloadedDocument result = documentService.downloadDocument(9L, principal(stagiaire));

        assertThat(result.content()).isEqualTo("bytes".getBytes());
        assertThat(result.fileName()).isEqualTo("x.pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }

    // Branch-wide review: previously untested — the inscription-scoped download path.
    @Test
    void downloadInscriptionDocumentByOwningStagiaireReturnsBytes() {
        User stagiaire = user(7L, Role.STAGIAIRE);
        Inscription inscription = Inscription.builder().id(5L).stagiaire(stagiaire).build();
        Document document = Document.builder().id(9L).inscription(inscription).storagePath("inscriptions/5/x.pdf")
                .fileName("x.pdf").mimeType("application/pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(storageService.download("inscriptions/5/x.pdf")).thenReturn("bytes".getBytes());

        DownloadedDocument result = documentService.downloadDocument(9L, principal(stagiaire));

        assertThat(result.content()).isEqualTo("bytes".getBytes());
    }

    @Test
    void downloadInscriptionDocumentByAnotherStagiaireReturnsForbidden() {
        Inscription inscription = Inscription.builder().id(5L).stagiaire(user(7L, Role.STAGIAIRE)).build();
        Document document = Document.builder().id(9L).inscription(inscription).storagePath("inscriptions/5/x.pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.downloadDocument(9L, principal(user(8L, Role.STAGIAIRE))))
                .isInstanceOf(UnauthorizedException.class);
    }

    // --- deleteDocument -------------------------------------------------------------------------

    @Test
    void deleteUnknownDocumentReturnsNotFound() {
        when(documentRepository.findById(404L)).thenReturn(Optional.empty());
        AdacUserDetails caller = principal(user(1L, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> documentService.deleteDocument(404L, caller)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteByStagiaireReturnsForbidden() {
        Document document = Document.builder().id(9L).uploadedBy(user(2L, Role.ADMIN)).build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        AdacUserDetails caller = principal(user(7L, Role.STAGIAIRE));

        assertThatThrownBy(() -> documentService.deleteDocument(9L, caller)).isInstanceOf(UnauthorizedException.class);
        verify(documentRepository, never()).delete(any());
        verifyNoInteractions(storageService);
    }

    @Test
    void deleteSomeoneElsesUploadByAdminReturnsForbidden() {
        Document document = Document.builder().id(9L).uploadedBy(user(2L, Role.ADMIN)).build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        AdacUserDetails caller = principal(user(3L, Role.ADMIN));

        assertThatThrownBy(() -> documentService.deleteDocument(9L, caller)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deleteOwnUploadByAdminSucceeds() {
        User admin = user(2L, Role.ADMIN);
        Document document = Document.builder().id(9L).uploadedBy(admin).storagePath("formations/1/x.pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));

        documentService.deleteDocument(9L, principal(admin));

        verify(documentRepository).delete(document);
        verify(storageService).delete("formations/1/x.pdf");
    }

    @Test
    void deleteAnyDocumentBySuperAdminSucceeds() {
        Document document = Document.builder().id(9L).uploadedBy(user(2L, Role.ADMIN)).storagePath("formations/1/x.pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));

        documentService.deleteDocument(9L, principal(user(1L, Role.SUPER_ADMIN)));

        verify(documentRepository).delete(document);
        verify(storageService).delete("formations/1/x.pdf");
    }

    // Branch-wide review (RGPD): a storage failure must not fail the DELETE request — the DB row
    // (the source of truth) is already gone.
    @Test
    void deleteSucceedsEvenWhenStorageDeleteFails() {
        Document document = Document.builder().id(9L).uploadedBy(user(1L, Role.SUPER_ADMIN)).storagePath("formations/1/x.pdf").build();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Supabase down")).when(storageService).delete("formations/1/x.pdf");

        documentService.deleteDocument(9L, principal(user(1L, Role.SUPER_ADMIN)));

        verify(documentRepository).delete(document);
    }
}
