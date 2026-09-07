package com.adac.portail.service;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.security.AdacUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    /**
     * Exactly one of {@code formationId}/{@code inscriptionId} must be non-null (see docs/tech.md
     * § 6). Role rules: SUPER_ADMIN either; ADMIN {@code formationId} only, and only their own
     * formation; STAGIAIRE {@code inscriptionId} only, and only their own inscription.
     */
    DocumentResponse uploadDocument(MultipartFile file, Long formationId, Long inscriptionId, AdacUserDetails principal);

    /** Same one-of-two-params rule as upload; visibility scoped per caller role. */
    List<DocumentResponse> getDocuments(Long formationId, Long inscriptionId, AdacUserDetails principal);

    DownloadedDocument downloadDocument(Long id, AdacUserDetails principal);

    /** SUPER_ADMIN any document; ADMIN only their own uploads; STAGIAIRE never. */
    void deleteDocument(Long id, AdacUserDetails principal);
}
