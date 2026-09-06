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

/**
 * See {@link DocumentService} for scope. Role/ownership rules are data-dependent (docs/tech.md
 * § 6), the same reasoning {@code FormationController}/{@code MessageController} already document
 * for not using {@code @PreAuthorize} here — {@code DocumentController} has none either.
 *
 * <p>Branch-wide review: {@link #uploadDocument} deliberately carries no {@code @Transactional} —
 * {@link StorageService#upload} is an external HTTP call, and wrapping it in a transaction would
 * pin a DB connection for the whole request (worse still, unboundedly, before this same review
 * added a timeout to {@code StorageServiceImpl}'s {@code RestTemplate}). Every repository call
 * below is individually transactional on its own (Spring Data's default), and the two
 * authorization helper families ({@code assertCanUploadTo*}/{@code assertCanViewFormation/
 * InscriptionDocuments}) only ever read a LAZY association's {@code id} off an already fully-
 * loaded root entity — safe without an open Hibernate session (same reasoning documented on
 * {@code FormationServiceImpl.isOwnFormation}). {@link #downloadDocument} keeps its
 * {@code @Transactional(readOnly = true)}, deliberately not split the same way: its authorization
 * check dereferences a *nested* LAZY proxy two hops deep (e.g. {@code document.getInscription()}
 * — itself unresolved — then {@code .getFormation().getFormateur()}), which does need an open
 * session; splitting that safely needs a real entity-graph fetch or a second Spring bean to escape
 * self-invocation, judged out of scope for this ticket — the added connect/read timeout bounds the
 * worst case instead of eliminating it. Flagged as a follow-up, not silently dropped.</p>
 */
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

        // Deliberately outside any transaction — see class Javadoc.
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
            // Branch-wide review: without this, a DB-side failure here (a constraint violation,
            // a dropped connection) would leave the file permanently orphaned in Supabase — it
            // was already durably written above, and nothing else would ever reference or clean
            // it up.
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
            // Branch-wide review (RGPD): log, don't fail the request — the database row (the
            // source of truth for "does this document still exist") is already gone; a Supabase
            // hiccup here shouldn't make DELETE non-idempotent or roll back an otherwise-successful
            // deletion. The object is now unreferenced either way and can be swept by a future
            // cleanup job if this ever fires.
            log.error("Failed to delete storage object {} for deleted document {}", path, id, e);
        }
    }

    // --- Authorization -------------------------------------------------------------------------

    /** docs/tech.md § 6: "ADMIN -> formationId uniquement (ses formations)". */
    private void assertCanUploadToFormation(Formation formation, User caller) {
        if (caller.getRole() == Role.STAGIAIRE) {
            throw new UnauthorizedException("Un stagiaire ne peut déposer un document que sur une inscription");
        }
        if (caller.getRole() == Role.ADMIN && !isOwnFormation(formation, caller)) {
            throw new UnauthorizedException(NOT_OWN_FORMATION_UPLOAD);
        }
    }

    /** docs/tech.md § 6: "STAGIAIRE -> inscriptionId uniquement (ses inscriptions)". */
    private void assertCanUploadToInscription(Inscription inscription, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            throw new UnauthorizedException("Un formateur ne peut déposer un document que sur une formation");
        }
        if (caller.getRole() == Role.STAGIAIRE && !inscription.getStagiaire().getId().equals(caller.getId())) {
            throw new UnauthorizedException("Vous ne pouvez déposer un document que sur votre propre inscription");
        }
    }

    /**
     * Branch-wide review (security): a non-owning ADMIN gets 404, not 403 — same "don't confirm
     * existence to someone with no right to see it" oracle-avoidance {@code
     * FormationServiceImpl.getFormationById} already documents for the identical case. STAGIAIRE
     * keeps 403 (they're allowed to know the formation exists, just not that they're unenrolled —
     * matches the existing GET /api/formations/{id} contract).
     */
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

    /**
     * ADMIN is allowed here (unlike upload) when the inscription belongs to one of their own
     * formations — a formateur can legitimately want to see a document targeted at their own
     * trainee even though they can't be the one to upload it that way (docs/tech.md doesn't spell
     * out this GET case explicitly; flagged in the ticket report as a judgment call, not a
     * contradiction of a stated acceptance criterion). Same 404-not-403 reasoning as {@link
     * #assertCanViewFormationDocuments} for the non-owning case.
     */
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

    /** Same LAZY-proxy-id-read reasoning as {@code FormationServiceImpl.isOwnFormation}. */
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
                // No self-notification when a stagiaire uploads to their own inscription
                // (branch-wide review) — they don't need telling about a document they just sent.
                notificationService.notify(inscription.getStagiaire().getId(), NotificationType.DOCUMENT_UPLOADED,
                        content, EntityType.FORMATION, inscription.getFormation().getId());
            }
        } catch (RuntimeException e) {
            // The document itself is already durably saved (see uploadDocument) — a notification
            // failure (e.g. a recipient deleted concurrently) is a secondary effect and must not
            // turn an otherwise-successful upload into an error response.
            log.warn("Notification failed for uploaded document {}", document.getId(), e);
        }
    }
}
