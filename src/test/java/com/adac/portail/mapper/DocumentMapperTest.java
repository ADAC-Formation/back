package com.adac.portail.mapper;

import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {DocumentMapperImpl.class, UserMapperImpl.class})
class DocumentMapperTest {

    @Autowired
    private DocumentMapper documentMapper;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private JsonNode toJson() {
        User formateur = User.builder().id(7L).email("formateur@adac.fr").nom("Durand").prenom("Paul")
                .role(Role.ADMIN).passwordHash("secret").build();
        Document document = Document.builder().id(1L).fileName("programme.pdf").fileSize(10L)
                .mimeType("application/pdf").fileUrl("https://supabase/x").storagePath("formations/1/x.pdf")
                .uploadedBy(formateur).formation(Formation.builder().id(3L).build()).build();
        return objectMapper.valueToTree(documentMapper.toResponse(document));
    }

    @Test
    void uploadedByExposesOnlyIdPrenomAndNom() {
        JsonNode uploadedBy = toJson().get("uploadedBy");

        List<String> fields = new ArrayList<>();
        uploadedBy.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "prenom", "nom");
        assertThat(uploadedBy.get("prenom").asText()).isEqualTo("Paul");
    }

    @Test
    void doesNotExposeFileUrl() {
        assertThat(toJson().has("fileUrl")).isFalse();
    }
}
