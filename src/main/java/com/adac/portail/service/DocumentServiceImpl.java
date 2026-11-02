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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private static final String DOCUMENT_NOT_FOUND = "Document introuvable";
    private static final String FORMATION_NOT_FOUND = "Formation introuvable";
    private static final String INSCRIPTION_NOT_FOUND = "Inscription introuvable";
    private static final String EXACTLY_ONE_TARGET =
            "Un document doit être lié à exactement une formation ou une inscription";
    private static final String NOT_OWN_FORMATION_UPLOAD =
            "Vous ne pouvez déposer un document que sur vos propres formations";
    private static final String NOT_OWN_FORMATION_VIEW =
            "Vous ne pouvez consulter que les documents de vos formations";

    private final DocumentRepository documentRepository;
    private final FormationRepository formationRepository;
    private final InscriptionRepository inscriptionRepository;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final NotificationService notificationService;

    @Override
    public DocumentResponse uploadDocument(MultipartFile file, Long formationId, Long inscriptionId,
                                            AdacUserDetails principal) {
        if ((formationId == null) == (inscriptionId == null)) {
            throw new BadRequestException(EXACTLY_ONE_TARGET);
        }
        fileValidator.validate(file);
        String mimeType = fileValidator.canonicalMimeType(file.getOriginalFilename());
        User caller = principal.getUser();

        Formation formation = null;
        Inscription inscription = null;
        String path;
        if (formationId != null) {
            formation = formationRepository.findById(formationId)
                    .orElseThrow(() -> new ResourceNotFoundException(FORMATION_NOT_FOUND));
            assertCanUploadToFormation(formation, caller);
            path = "formations/%d/%s-%s".formatted(formationId, UUID.randomUUID(), file.getOriginalFilename());
        } else {
            inscription = inscriptionRepository.findById(inscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException(INSCRIPTION_NOT_FOUND));
            assertCanUploadToInscription(inscription, caller);
            path = "inscriptions/%d/%s-%s".formatted(inscriptionId, UUID.randomUUID(), file.getOriginalFilename());
        }

        String fileUrl = storageService.upload(file, path, mimeType);

        Document saved;
        try {
            saved = documentRepository.save(Document.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .mimeType(mimeType)
                    .uploadedBy(caller)
                    .formation(formation)
                    .inscription(inscription)
                    .fileUrl(fileUrl)
                    .storagePath(path)
                    .build());
        } catch (RuntimeException e) {

            log.warn("Document persistence failed after a successful Supabase upload — deleting {}", path);
            storageService.delete(path);
            throw e;
        }

        notifyTargets(saved, formation, inscription);

        return documentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(Long formationId, Long inscriptionId, AdacUserDetails principal) {
        if ((formationId == null) == (inscriptionId == null)) {
            throw new BadRequestException("Fournir exactement un des deux paramètres formationId ou inscriptionId");
        }
        User caller = principal.getUser();
        List<Document> documents;
        if (formationId != null) {
            Formation formation = formationRepository.findById(formationId)
                    .orElseThrow(() -> new ResourceNotFoundException(FORMATION_NOT_FOUND));
            assertCanViewFormationDocuments(formation, caller);
            documents = documentRepository.findAllByFormation(formation);
        } else {
            Inscription inscription = inscriptionRepository.findById(inscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException(INSCRIPTION_NOT_FOUND));
            assertCanViewInscriptionDocuments(inscription, caller);
            documents = documentRepository.findAllByInscription(inscription);
        }
        return documents.stream().map(documentMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedDocument downloadDocument(Long id, AdacUserDetails principal) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(DOCUMENT_NOT_FOUND));
        User caller = principal.getUser();
        if (document.getFormation() != null) {
            assertCanViewFormationDocuments(document.getFormation(), caller);
        } else {
            assertCanViewInscriptionDocuments(document.getInscription(), caller);
        }
        byte[] content = storageService.download(document.getStoragePath());
        return new DownloadedDocument(content, document.getFileName(), document.getMimeType());
    }

    @Override
    @Transactional
    public void deleteDocument(Long id, AdacUserDetails principal) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(DOCUMENT_NOT_FOUND));
        User caller = principal.getUser();
        if (caller.getRole() == Role.STAGIAIRE) {
            throw new UnauthorizedException("Droits insuffisants");
        }
        if (caller.getRole() == Role.ADMIN && !document.getUploadedBy().getId().equals(caller.getId())) {
            throw new UnauthorizedException("Vous ne pouvez supprimer que vos propres documents");
        }
        String path = document.getStoragePath();
        documentRepository.delete(document);
        try {
            storageService.delete(path);
        } catch (RuntimeException e) {

            log.error("Failed to delete storage object {} for deleted document {}", path, id, e);
        }
    }

    private void assertCanUploadToFormation(Formation formation, User caller) {
        if (caller.getRole() == Role.STAGIAIRE) {
            throw new UnauthorizedException("Un stagiaire ne peut déposer un document que sur une inscription");
        }
        if (caller.getRole() == Role.ADMIN && !isOwnFormation(formation, caller)) {
            throw new UnauthorizedException(NOT_OWN_FORMATION_UPLOAD);
        }
    }

    private void assertCanUploadToInscription(Inscription inscription, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            throw new UnauthorizedException("Un formateur ne peut déposer un document que sur une formation");
        }
        if (caller.getRole() == Role.STAGIAIRE && !inscription.getStagiaire().getId().equals(caller.getId())) {
            throw new UnauthorizedException("Vous ne pouvez déposer un document que sur votre propre inscription");
        }
    }

    private void assertCanViewFormationDocuments(Formation formation, User caller) {
        switch (caller.getRole()) {
            case SUPER_ADMIN -> { }
            case ADMIN -> {
                if (!isOwnFormation(formation, caller)) {
                    throw new ResourceNotFoundException(FORMATION_NOT_FOUND);
                }
            }
            case STAGIAIRE -> {
                if (!inscriptionRepository.existsByStagiaireAndFormation(caller, formation)) {
                    throw new UnauthorizedException("Vous n'êtes pas inscrit à cette formation");
                }
            }
        }
    }

    private void assertCanViewInscriptionDocuments(Inscription inscription, User caller) {
        switch (caller.getRole()) {
            case SUPER_ADMIN -> { }
            case ADMIN -> {
                if (!isOwnFormation(inscription.getFormation(), caller)) {
                    throw new ResourceNotFoundException(INSCRIPTION_NOT_FOUND);
                }
            }
            case STAGIAIRE -> {
                if (!inscription.getStagiaire().getId().equals(caller.getId())) {
                    throw new UnauthorizedException("Vous ne pouvez consulter que vos propres documents");
                }
            }
        }
    }

    private boolean isOwnFormation(Formation formation, User caller) {
        return formation.getFormateur() != null && formation.getFormateur().getId().equals(caller.getId());
    }

    private void notifyTargets(Document document, Formation formation, Inscription inscription) {
        String content = "Nouveau document déposé : " + document.getFileName();
        try {
            if (formation != null) {
                for (User stagiaire : inscriptionRepository.findStagiairesByFormation(formation)) {
                    notificationService.notify(stagiaire.getId(), NotificationType.DOCUMENT_UPLOADED, content,
                            EntityType.FORMATION, formation.getId());
                }
            } else if (!inscription.getStagiaire().getId().equals(document.getUploadedBy().getId())) {

                notificationService.notify(inscription.getStagiaire().getId(), NotificationType.DOCUMENT_UPLOADED,
                        content, EntityType.FORMATION, inscription.getFormation().getId());
            }
        } catch (RuntimeException e) {

            log.warn("Notification failed for uploaded document {}", document.getId(), e);
        }
    }
}
