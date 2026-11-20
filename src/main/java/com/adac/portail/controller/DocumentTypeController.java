package com.adac.portail.controller;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.service.DocumentTypeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
@RequiredArgsConstructor
@Tag(name = "Document types")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    @Operation(summary = "Create a document type", description = "SUPER_ADMIN only.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = DocumentTypeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid field, scope without categories, incomplete deadline or unknown format",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Unknown category",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Name already in use (case-insensitive)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DocumentTypeResponse> createDocumentType(@Valid @RequestBody DocumentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentTypeService.createDocumentType(request));
    }

    @Operation(summary = "List document types", description = "SUPER_ADMIN only, sorted by name. ?active=true restricts to active types.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<DocumentTypeResponse>> getDocumentTypes(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(documentTypeService.getDocumentTypes(active));
    }

    @Operation(summary = "Update a document type", description = "SUPER_ADMIN only. Replaces every field; isActive is untouched.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = DocumentTypeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such document type, or unknown category",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Name already used by another type",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DocumentTypeResponse> updateDocumentType(
            @PathVariable @Parameter(description = "Document type id") Long id,
            @Valid @RequestBody DocumentTypeRequest request) {
        return ResponseEntity.ok(documentTypeService.updateDocumentType(id, request));
    }

    @Operation(summary = "Activate a document type", description = "SUPER_ADMIN only. Idempotent.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = DocumentTypeResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such document type",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DocumentTypeResponse> activateDocumentType(@PathVariable Long id) {
        return ResponseEntity.ok(documentTypeService.activateDocumentType(id));
    }

    @Operation(summary = "Deactivate a document type", description = "SUPER_ADMIN only. Idempotent. A type is never deleted.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = DocumentTypeResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such document type",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DocumentTypeResponse> deactivateDocumentType(@PathVariable Long id) {
        return ResponseEntity.ok(documentTypeService.deactivateDocumentType(id));
    }
}
