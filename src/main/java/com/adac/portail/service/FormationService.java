package com.adac.portail.service;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * Formation CRUD + archiving (US-004) — see docs/tech.md § 4 for the wire shapes and
 * {@link com.adac.portail.controller.FormationController} for the role rules on each route.
 */
public interface FormationService {

    /**
     * SUPER_ADMIN only (enforced by the controller). Auto-assigns the calling Super Admin as
     * {@code formateur} when {@code request.getFormateurId()} is {@code null}.
     */
    FormationResponse createFormation(CreateFormationRequest request, AdacUserDetails principal);

    /**
     * Role-scoped: a SUPER_ADMIN sees every formation, an ADMIN only the ones they teach, a
     * STAGIAIRE only the ones they're enrolled in — {@code status}/{@code categoryId}, when given,
     * filter further within that scope.
     */
    List<FormationResponse> getFormations(FormationStatus status, Long categoryId, AdacUserDetails principal);

    /**
     * Any authenticated role may call this, but a STAGIAIRE gets a 403
     * ({@link org.springframework.security.access.AccessDeniedException}) unless enrolled.
     */
    FormationResponse getFormationById(Long id, AdacUserDetails principal);

    /**
     * SUPER_ADMIN only (enforced by the controller — no caller-specific rule needs the principal
     * here). Only the fields present (non-null) on {@code request} are applied. Throws
     * {@link com.adac.portail.exception.FormationArchivedException} if the formation is already
     * {@link FormationStatus#ARCHIVED}.
     */
    FormationResponse updateFormation(Long id, UpdateFormationRequest request);

    /** SUPER_ADMIN only. Idempotent — archiving an already-archived formation is a no-op. */
    FormationResponse archiveFormation(Long id);
}
