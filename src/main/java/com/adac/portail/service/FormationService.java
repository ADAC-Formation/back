package com.adac.portail.service;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.security.AdacUserDetails;
import org.springframework.web.multipart.MultipartFile;

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
     * Same visibility rule as {@link #getFormationById}, returning the entity instead of the
     * mapped DTO — {@code getFormationById} delegates to this (TICKET-023 review), and
     * {@code InscriptionServiceImpl.getInscriptions} calls it directly so the roster's visibility
     * check doesn't require building (and discarding) a whole {@link FormationResponse} — including
     * its own {@code countByFormation} query — just to re-fetch the same entity a line later.
     */
    Formation findVisibleFormationOrThrow(Long id, AdacUserDetails principal);

    /**
     * SUPER_ADMIN only (enforced by the controller — no caller-specific rule needs the principal
     * here). Only the fields present (non-null) on {@code request} are applied. Throws
     * {@link com.adac.portail.exception.FormationArchivedException} if the formation is already
     * {@link FormationStatus#ARCHIVED}.
     */
    FormationResponse updateFormation(Long id, UpdateFormationRequest request);

    /** SUPER_ADMIN only. Idempotent — archiving an already-archived formation is a no-op. */
    FormationResponse archiveFormation(Long id);

    /**
     * SUPER_ADMIN only (US-005). All-or-nothing: {@code ExcelImportUtil} validates every row
     * before any formation is created — see its Javadoc. Each parsed row is created exactly like
     * {@link #createFormation}, including {@code formateurId} auto-assignment to {@code principal}.
     */
    List<FormationResponse> importFormations(MultipartFile file, AdacUserDetails principal);
}
