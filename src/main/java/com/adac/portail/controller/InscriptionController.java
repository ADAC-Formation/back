package com.adac.portail.controller;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.InscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Formation enrollment (US-006) — see docs/tech.md § 5.
 *
 * <p>{@code GET} is SUPER_ADMIN/ADMIN only (review) — every {@code InscriptionResponse.stagiaire}
 * is a full {@code UserResponse} (email included), so letting an enrolled STAGIAIRE call it would
 * hand them every co-trainee's email address, not just "who else is in this session"; ADMIN
 * visibility is still scoped to their own formations inside {@code InscriptionServiceImpl} (reuses
 * {@code FormationService.findVisibleFormationOrThrow}). {@code POST}/{@code DELETE} are
 * SUPER_ADMIN only, same convention as {@code FormationController}'s write routes.</p>
 */
@RestController
@RequestMapping("/api/formations/{formationId}/inscriptions")
@RequiredArgsConstructor
@Tag(name = "Inscriptions")
public class InscriptionController {

    private final InscriptionService inscriptionService;

    @Operation(summary = "List enrolled stagiaires", description = "SUPER_ADMIN/ADMIN only — ADMIN scoped to their own formations, same as GET /api/formations/{id}.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation, or ADMIN not its formateur",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<InscriptionResponse>> getInscriptions(
            @PathVariable @Parameter(description = "Formation id") Long formationId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(inscriptionService.getInscriptions(formationId, principal));
    }

    @Operation(summary = "Enroll a stagiaire", description = "SUPER_ADMIN only.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = InscriptionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Formation archived",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation, or stagiaireId isn't an active STAGIAIRE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Already enrolled",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InscriptionResponse> createInscription(
            @PathVariable @Parameter(description = "Formation id") Long formationId,
            @Valid @RequestBody InscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inscriptionService.createInscription(formationId, request));
    }

    @Operation(summary = "Unenroll a stagiaire", description = "SUPER_ADMIN only. Idempotent.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{stagiaireId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteInscription(
            @PathVariable @Parameter(description = "Formation id") Long formationId,
            @PathVariable @Parameter(description = "Stagiaire id") Long stagiaireId) {
        inscriptionService.deleteInscription(formationId, stagiaireId);
        return ResponseEntity.noContent().build();
    }
}
