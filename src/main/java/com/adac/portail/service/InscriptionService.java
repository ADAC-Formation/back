package com.adac.portail.service;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

public interface InscriptionService {

    List<InscriptionResponse> getInscriptions(Long formationId, AdacUserDetails principal);

    InscriptionResponse createInscription(Long formationId, InscriptionRequest request);

    void deleteInscription(Long formationId, Long stagiaireId);
}
