package com.adac.portail.controller;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.DocumentService;
import com.adac.portail.service.DownloadedDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Document upload/list/download/delete (US-009, US-010) — see docs/tech.md § 6. No
 * {@code @PreAuthorize}: every rule (which target is allowed per role, ownership, enrollment) is
 * data-dependent, the same reasoning already documented on {@code FormationController} and
 * {@code MessageController} — {@link DocumentService} enforces them and throws {@code
 * UnauthorizedException} (403) when violated.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a document", description = "multipart/form-data. Exactly one of formationId/inscriptionId. SUPER_ADMIN either; ADMIN formationId (own formations) only; STAGIAIRE inscriptionId (own) only.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Both or neither target given, disallowed format, or file too large",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role for this target",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation or inscription",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long formationId,
            @RequestParam(required = false) Long inscriptionId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(file, formationId, inscriptionId, principal));
    }

    @Operation(summary = "List documents", description = "Exactly one of ?formationId or ?inscriptionId. Visibility scoped by caller role — see docs/tech.md § 6.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentResponse.class))))
    @ApiResponse(responseCode = "400", description = "Both or neither query param given",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role for this target",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @RequestParam(required = false) Long formationId,
            @RequestParam(required = false) Long inscriptionId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(documentService.getDocuments(formationId, inscriptionId, principal));
    }

    @Operation(summary = "Download a document", description = "Streams the file bytes, proxied from Supabase Storage.")
    @ApiResponse(responseCode = "200", description = "OK — binary stream")
    @ApiResponse(responseCode = "403", description = "Insufficient role for this document",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such document",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable @Parameter(description = "Document id") Long id,
            @AuthenticationPrincipal AdacUserDetails principal) {
        DownloadedDocument document = documentService.downloadDocument(id, principal);
        // Branch-wide review (security): never hand-build this header from a filename the client
        // chose — a `"` in a quoted-string filename parameter can inject a second `filename*`
        // parameter that overrides what the browser saves the download as. ContentDisposition
        // quotes/escapes correctly and emits the RFC 5987 filename* form for non-ASCII names
        // (FileValidator additionally rejects `"`/`/`/control characters outright at upload time).
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.content());
    }

    @Operation(summary = "Delete a document", description = "SUPER_ADMIN any document; ADMIN only their own uploads; STAGIAIRE never.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such document",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, @AuthenticationPrincipal AdacUserDetails principal) {
        documentService.deleteDocument(id, principal);
        return ResponseEntity.noContent().build();
    }
}
