package com.adac.portail.service;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * Formation enrollment (US-006) — see docs/tech.md § 5 for the wire shapes and
 * {@link com.adac.portail.controller.InscriptionController} for the role rules on each route.
 */
public interface InscriptionService {

    /**
     * SUPER_ADMIN/ADMIN only (enforced by the controller — review: a STAGIAIRE used to be allowed
     * through if enrolled, but {@code InscriptionResponse.stagiaire} is a full {@code UserResponse}
     * including email, so that would hand every enrolled stagiaire their co-trainees' emails).
     * ADMIN visibility is still scoped to their own formations via
     * {@code FormationService.findVisibleFormationOrThrow} — the same rule {@code getFormationById}
     * uses, reused here rather than duplicated.
     */
    List<InscriptionResponse> getInscriptions(Long formationId, AdacUserDetails principal);

    /**
     * SUPER_ADMIN only (enforced by the controller). Throws
     * {@link com.adac.portail.exception.FormationArchivedException} if the formation is archived,
     * {@link com.adac.portail.exception.DuplicateInscriptionException} if already enrolled.
     */
    InscriptionResponse createInscription(Long formationId, InscriptionRequest request);

    /** SUPER_ADMIN only. Idempotent — desinscribing a stagiaire not enrolled is a no-op. */
    void deleteInscription(Long formationId, Long stagiaireId);
}
