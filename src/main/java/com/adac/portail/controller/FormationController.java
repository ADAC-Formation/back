package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.FormationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Formation CRUD + archiving (US-004) — see docs/tech.md § 4.
 *
 * <p>Write routes ({@code POST}, {@code PUT}, {@code .../archive}) are SUPER_ADMIN only
 * ({@code @PreAuthorize}, same convention as {@code CategoryController}); {@code GET} has none —
 * every authenticated role may call it, with the actual visibility scoped in
 * {@code FormationServiceImpl} (SUPER_ADMIN sees everything, ADMIN their own formations, STAGIAIRE
 * their enrollments).</p>
 */
@RestController
@RequestMapping("/api/formations")
@RequiredArgsConstructor
@Tag(name = "Formations")
public class FormationController {

    private final FormationService formationService;

    @Operation(summary = "Create formation", description = "SUPER_ADMIN only. formateurId null auto-assigns the caller.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = FormationResponse.class)))
    @ApiResponse(responseCode = "400", description = "categoryId missing or unknown, or formateurId invalid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FormationResponse> createFormation(
            @Valid @RequestBody CreateFormationRequest request,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.createFormation(request, principal));
    }

    @Operation(summary = "Import formations from Excel", description = "SUPER_ADMIN only. All-or-nothing: any invalid row rejects the whole file, see docs/tech.md.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FormationResponse.class))))
    @ApiResponse(responseCode = "400", description = "Not a .xlsx file, or a row is invalid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/import")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FormationResponse>> importFormations(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.importFormations(file, principal));
    }

    @Operation(summary = "List formations", description = "SUPER_ADMIN sees all; ADMIN sees only formations they teach; STAGIAIRE sees only their enrollments. ?status and ?categoryId filter further within that scope.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    public ResponseEntity<List<FormationResponse>> getFormations(
            @RequestParam(required = false) FormationStatus status,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(formationService.getFormations(status, categoryId, principal));
    }

    @Operation(summary = "Get formation detail", description = "Any authenticated role — a STAGIAIRE not enrolled gets 403.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = FormationResponse.class)))
    @ApiResponse(responseCode = "403", description = "STAGIAIRE not enrolled",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<FormationResponse> getFormationById(
            @PathVariable @Parameter(description = "Formation id") Long id,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(formationService.getFormationById(id, principal));
    }

    @Operation(summary = "Update formation", description = "SUPER_ADMIN only. Only the fields present in the body are applied. 400 if the formation is archived.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = FormationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Formation archived, categoryId unknown, or formateurId invalid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Optimistic lock conflict — modified concurrently",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FormationResponse> updateFormation(
            @PathVariable @Parameter(description = "Formation id") Long id,
            @Valid @RequestBody UpdateFormationRequest request) {
        return ResponseEntity.ok(formationService.updateFormation(id, request));
    }

    @Operation(summary = "Archive formation", description = "SUPER_ADMIN only. Irreversible; idempotent.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = FormationResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FormationResponse> archiveFormation(@PathVariable Long id) {
        return ResponseEntity.ok(formationService.archiveFormation(id));
    }
}
