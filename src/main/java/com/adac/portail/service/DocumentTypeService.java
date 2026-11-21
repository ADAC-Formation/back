package com.adac.portail.service;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

public interface DocumentTypeService {

    DocumentTypeResponse createDocumentType(DocumentTypeRequest request);

    List<DocumentTypeResponse> getDocumentTypes(Boolean active);

    /**
     * Types actifs à envoyer pour une formation, triés par nom. Mêmes droits que la lecture de la formation :
     * un stagiaire inscrit, le formateur de la formation, la chargée de formation.
     */
    List<DocumentTypeResponse> getDocumentTypesForFormation(Long formationId, AdacUserDetails principal);

    DocumentTypeResponse updateDocumentType(Long id, DocumentTypeRequest request);

    DocumentTypeResponse activateDocumentType(Long id);

    DocumentTypeResponse deactivateDocumentType(Long id);
}
