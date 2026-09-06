package com.adac.portail.controller;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.exception.UnauthorizedException;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.DocumentService;
import com.adac.portail.service.DownloadedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link DocumentController} — TICKET-026. Same {@code addFilters = false} +
 * {@link WithMockAdacUser} pattern as {@code MessageControllerTest} / {@code FormationControllerTest}
 * — no {@code @PreAuthorize} here either, every role-and-ownership rule is data-dependent and
 * enforced by {@link DocumentService}.
 */
@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // --- POST /api/documents --------------------------------------------------------------

    // Ticket Test 1: valid file + formationId -> 201.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void uploadValidFileWithFormationIdReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "programme.pdf", "application/pdf", "contenu".getBytes());
        when(documentService.uploadDocument(any(), eq(1L), isNull(), any()))
                .thenReturn(DocumentResponse.builder().id(1L).fileName("programme.pdf").build());

        mockMvc.perform(multipart("/api/documents").file(file).param("formationId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("programme.pdf"));
    }

    // Ticket Test 2: both formationId and inscriptionId -> 400.
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void uploadWithBothTargetsReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "programme.pdf", "application/pdf", "contenu".getBytes());
        doThrow(new BadRequestException("Un document ne peut pas être lié aux deux"))
                .when(documentService).uploadDocument(any(), eq(1L), eq(2L), any());

        mockMvc.perform(multipart("/api/documents").file(file)
                        .param("formationId", "1").param("inscriptionId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void uploadWithNeitherTargetReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "programme.pdf", "application/pdf", "contenu".getBytes());
        doThrow(new BadRequestException("Un document doit être lié à une formation ou une inscription"))
                .when(documentService).uploadDocument(any(), isNull(), isNull(), any());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest());
    }

    // Ticket Test 3: disallowed file type -> 400 (mapped from the service's BadRequestException —
    // FileValidatorTest covers the actual format/size rules).
    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void uploadWithDisallowedFileTypeReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());
        doThrow(new BadRequestException("Format non autorisé"))
                .when(documentService).uploadDocument(any(), eq(1L), isNull(), any());

        mockMvc.perform(multipart("/api/documents").file(file).param("formationId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Format non autorisé"));
    }

    // Ticket Test 4: ADMIN upload on another formateur's formation -> 403 (mapped from the
    // service's UnauthorizedException — the real role-matrix coverage lives in
    // DocumentServiceImplTest; this only proves the controller/GlobalExceptionHandler wiring).
    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void uploadOnAnotherFormateursFormationReturnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "programme.pdf", "application/pdf", "contenu".getBytes());
        doThrow(new UnauthorizedException("Vous ne pouvez déposer un document que sur vos propres formations"))
                .when(documentService).uploadDocument(any(), eq(1L), isNull(), any());

        mockMvc.perform(multipart("/api/documents").file(file).param("formationId", "1"))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/documents ---------------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getDocumentsByFormationIdReturnsListFromService() throws Exception {
        when(documentService.getDocuments(eq(1L), isNull(), any()))
                .thenReturn(List.of(DocumentResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/documents").param("formationId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getDocumentsWithoutAnyParamReturnsBadRequest() throws Exception {
        doThrow(new BadRequestException("Fournir formationId ou inscriptionId"))
                .when(documentService).getDocuments(isNull(), isNull(), any());

        mockMvc.perform(get("/api/documents")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void getDocumentsForFormationNotEnrolledReturnsForbidden() throws Exception {
        doThrow(new UnauthorizedException("Vous n'êtes pas inscrit à cette formation"))
                .when(documentService).getDocuments(eq(404L), isNull(), any());

        mockMvc.perform(get("/api/documents").param("formationId", "404")).andExpect(status().isForbidden());
    }

    // --- GET /api/documents/{id}/download ---------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void downloadReturnsBinaryStreamWithContentTypeAndFilename() throws Exception {
        when(documentService.downloadDocument(eq(9L), any()))
                .thenReturn(new DownloadedDocument("contenu-pdf".getBytes(), "programme.pdf", "application/pdf"));

        mockMvc.perform(get("/api/documents/9/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                // Branch-wide review: ContentDisposition.filename(name, UTF_8) always emits both
                // an RFC 2047 Q-encoded quoted-string form and the RFC 5987 filename* form once a
                // charset is given (see its source) — not the raw concatenation the original
                // version built.
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"=?UTF-8?Q?programme.pdf?=\"; filename*=UTF-8''programme.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes("contenu-pdf".getBytes()));
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void downloadUnknownDocumentReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Document introuvable"))
                .when(documentService).downloadDocument(eq(404L), any());

        mockMvc.perform(get("/api/documents/404/download")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void downloadUnauthorizedDocumentReturnsForbidden() throws Exception {
        doThrow(new UnauthorizedException("Droits insuffisants"))
                .when(documentService).downloadDocument(eq(9L), any());

        mockMvc.perform(get("/api/documents/9/download")).andExpect(status().isForbidden());
    }

    // --- DELETE /api/documents/{id} ---------------------------------------------------------

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void deleteDocumentReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/documents/9")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void deleteByStagiaireReturnsForbidden() throws Exception {
        doThrow(new UnauthorizedException("Droits insuffisants")).when(documentService).deleteDocument(eq(9L), any());

        mockMvc.perform(delete("/api/documents/9")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockAdacUser(role = Role.SUPER_ADMIN)
    void deleteUnknownDocumentReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Document introuvable")).when(documentService).deleteDocument(eq(404L), any());

        mockMvc.perform(delete("/api/documents/404")).andExpect(status().isNotFound());
    }
}
