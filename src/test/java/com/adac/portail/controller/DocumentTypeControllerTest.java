package com.adac.portail.controller;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.enums.DeadlineAnchor;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.DocumentTypeAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.service.DocumentTypeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DocumentTypeControllerTest.MethodSecurityTestConfig.class)
class DocumentTypeControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentTypeService documentTypeService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("nom", "Certificat médical");
        body.put("aide", "Moins de 3 mois");
        body.put("isRequired", true);
        body.put("isSensitive", true);
        body.put("appliesToAllFormations", false);
        body.put("categoryIds", List.of(1, 2));
        body.put("deadlineDays", 7);
        body.put("deadlineAnchor", "BEFORE_START");
        body.put("allowedFormats", List.of("pdf", "jpg"));
        return body;
    }

    private static DocumentTypeResponse response() {
        return DocumentTypeResponse.builder()
                .id(4L).nom("Certificat médical").aide("Moins de 3 mois")
                .required(true).sensitive(true).appliesToAllFormations(false)
                .categories(List.of(CategoryResponse.builder().id(1L).nom("Secourisme").couleur("#D62828").active(true).build()))
                .deadlineDays(7).deadlineAnchor(DeadlineAnchor.BEFORE_START)
                .allowedFormats(List.of("pdf", "jpg")).active(true).build();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // --- création -------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createReturnsCreatedWithEveryFieldOfTheResponse() throws Exception {
        when(documentTypeService.createDocumentType(any())).thenReturn(response());

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.nom").value("Certificat médical"))
                .andExpect(jsonPath("$.aide").value("Moins de 3 mois"))
                .andExpect(jsonPath("$.isRequired").value(true))
                .andExpect(jsonPath("$.isSensitive").value(true))
                .andExpect(jsonPath("$.appliesToAllFormations").value(false))
                .andExpect(jsonPath("$.categories[0].nom").value("Secourisme"))
                .andExpect(jsonPath("$.deadlineDays").value(7))
                .andExpect(jsonPath("$.deadlineAnchor").value("BEFORE_START"))
                .andExpect(jsonPath("$.allowedFormats[1]").value("jpg"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createReadsTheBodyFieldNamesOfTheContract() throws Exception {
        when(documentTypeService.createDocumentType(any())).thenReturn(response());

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isCreated());

        ArgumentCaptor<DocumentTypeRequest> captor = ArgumentCaptor.forClass(DocumentTypeRequest.class);
        verify(documentTypeService).createDocumentType(captor.capture());
        DocumentTypeRequest request = captor.getValue();
        assertThat(request.getNom()).isEqualTo("Certificat médical");
        assertThat(request.getRequired()).isTrue();
        assertThat(request.getSensitive()).isTrue();
        assertThat(request.getAppliesToAllFormations()).isFalse();
        assertThat(request.getCategoryIds()).containsExactly(1L, 2L);
        assertThat(request.getDeadlineDays()).isEqualTo(7);
        assertThat(request.getDeadlineAnchor()).isEqualTo(DeadlineAnchor.BEFORE_START);
        assertThat(request.getAllowedFormats()).containsExactly("pdf", "jpg");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isForbidden());

        verify(documentTypeService, never()).createDocumentType(any());
    }

    @Test
    @WithMockUser(roles = "STAGIAIRE")
    void createByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithABlankNameReturnsBadRequestWithoutCallingTheService() throws Exception {
        Map<String, Object> body = validBody();
        body.put("nom", "  ");

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(documentTypeService, never()).createDocumentType(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithoutTheRequiredFlagsReturnsBadRequest() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("isRequired");

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        verify(documentTypeService, never()).createDocumentType(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithDeadlineDaysOver365ReturnsBadRequest() throws Exception {
        Map<String, Object> body = validBody();
        body.put("deadlineDays", 366);

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithAHelpTextOver500CharactersReturnsBadRequest() throws Exception {
        Map<String, Object> body = validBody();
        body.put("aide", "a".repeat(501));

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithAnUnknownDeadlineAnchorReturnsBadRequest() throws Exception {
        Map<String, Object> body = validBody();
        body.put("deadlineAnchor", "WHENEVER");

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithANullFormatInTheListReturnsBadRequest() throws Exception {
        Map<String, Object> body = validBody();
        List<String> formats = new java.util.ArrayList<>();
        formats.add("pdf");
        formats.add(null);
        body.put("allowedFormats", formats);

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        verify(documentTypeService, never()).createDocumentType(any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithTooManyFormatsOrCategoriesReturnsBadRequest() throws Exception {
        Map<String, Object> tooManyFormats = validBody();
        tooManyFormats.put("allowedFormats", java.util.Collections.nCopies(21, "pdf"));
        Map<String, Object> tooManyCategories = validBody();
        tooManyCategories.put("categoryIds", java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList());

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(tooManyFormats)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(tooManyCategories)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithADuplicateNameReturnsConflict() throws Exception {
        when(documentTypeService.createDocumentType(any()))
                .thenThrow(new DocumentTypeAlreadyExistsException("Ce type de document existe déjà"));

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ce type de document existe déjà"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createPassesTheBusinessRuleMessageOfABadRequestThrough() throws Exception {
        when(documentTypeService.createDocumentType(any())).thenThrow(new BadRequestException("Format non autorisé : gif"));

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Format non autorisé : gif"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createWithAnUnknownCategoryReturnsNotFound() throws Exception {
        when(documentTypeService.createDocumentType(any())).thenThrow(new ResourceNotFoundException("Catégorie introuvable"));

        mockMvc.perform(post("/api/document-types").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isNotFound());
    }

    // --- liste ----------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listReturnsEveryTypeAndAsksForAllWhenNoFilter() throws Exception {
        when(documentTypeService.getDocumentTypes(null)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Certificat médical"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listPassesTheActiveFilter() throws Exception {
        when(documentTypeService.getDocumentTypes(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/document-types").param("active", "true")).andExpect(status().isOk());

        verify(documentTypeService).getDocumentTypes(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/document-types")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAGIAIRE")
    void listByStagiaireReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/document-types")).andExpect(status().isForbidden());
    }

    // --- modification ---------------------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateReturnsTheUpdatedType() throws Exception {
        when(documentTypeService.updateDocumentType(eq(4L), any())).thenReturn(response());

        mockMvc.perform(put("/api/document-types/4").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateOfAnUnknownTypeReturnsNotFound() throws Exception {
        when(documentTypeService.updateDocumentType(eq(404L), any()))
                .thenThrow(new ResourceNotFoundException("Type de document introuvable"));

        mockMvc.perform(put("/api/document-types/404").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateWithANameUsedByAnotherTypeReturnsConflict() throws Exception {
        when(documentTypeService.updateDocumentType(eq(4L), any()))
                .thenThrow(new DocumentTypeAlreadyExistsException("Ce type de document existe déjà"));

        mockMvc.perform(put("/api/document-types/4").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(put("/api/document-types/4").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isForbidden());
    }

    // --- activation -----------------------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void activateAndDeactivateReturnTheType() throws Exception {
        when(documentTypeService.activateDocumentType(4L)).thenReturn(response());
        when(documentTypeService.deactivateDocumentType(4L)).thenReturn(response());

        mockMvc.perform(patch("/api/document-types/4/activate")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/document-types/4/deactivate")).andExpect(status().isOk());

        verify(documentTypeService).activateDocumentType(4L);
        verify(documentTypeService).deactivateDocumentType(4L);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void activationOfAnUnknownTypeReturnsNotFound() throws Exception {
        when(documentTypeService.deactivateDocumentType(404L)).thenThrow(new ResourceNotFoundException("Type de document introuvable"));

        mockMvc.perform(patch("/api/document-types/404/deactivate")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activationByAdminReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/document-types/4/activate")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/document-types/4/deactivate")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void thereIsNoDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/document-types/4")).andExpect(status().isMethodNotAllowed());
    }
}
