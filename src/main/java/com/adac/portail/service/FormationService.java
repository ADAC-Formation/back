package com.adac.portail.service;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.security.AdacUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FormationService {

    FormationResponse createFormation(CreateFormationRequest request, AdacUserDetails principal);

    List<FormationResponse> getFormations(FormationStatus status, Long categoryId, AdacUserDetails principal);

    FormationResponse getFormationById(Long id, AdacUserDetails principal);

    Formation findVisibleFormationOrThrow(Long id, AdacUserDetails principal);

    FormationResponse updateFormation(Long id, UpdateFormationRequest request);

    FormationResponse archiveFormation(Long id);

    List<FormationResponse> importFormations(MultipartFile file, AdacUserDetails principal);
}
