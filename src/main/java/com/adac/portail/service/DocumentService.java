package com.adac.portail.service;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.security.AdacUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentResponse uploadDocument(MultipartFile file, Long formationId, Long inscriptionId, AdacUserDetails principal);

    List<DocumentResponse> getDocuments(Long formationId, Long inscriptionId, AdacUserDetails principal);

    DownloadedDocument downloadDocument(Long id, AdacUserDetails principal);

    void deleteDocument(Long id, AdacUserDetails principal);
}
