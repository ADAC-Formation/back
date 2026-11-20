package com.adac.portail.service;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.DocumentTypeResponse;

import java.util.List;

public interface DocumentTypeService {

    DocumentTypeResponse createDocumentType(DocumentTypeRequest request);

    List<DocumentTypeResponse> getDocumentTypes(Boolean active);

    DocumentTypeResponse updateDocumentType(Long id, DocumentTypeRequest request);

    DocumentTypeResponse activateDocumentType(Long id);

    DocumentTypeResponse deactivateDocumentType(Long id);
}
