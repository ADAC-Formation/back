package com.adac.portail.mapper;

import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.DocumentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Arrays;
import java.util.List;

// unmappedTargetPolicy ERROR : un champ de la réponse sans source (nom différent) ne doit jamais rester à sa valeur par défaut.
@Mapper(componentModel = "spring", uses = CategoryMapper.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DocumentTypeMapper {

    @Mapping(target = "appliesToAllFormations", source = "appliesToAll")
    @Mapping(target = "allowedFormats", source = "allowedFormats", qualifiedByName = "splitFormats")
    DocumentTypeResponse toResponse(DocumentType documentType);

    @Named("splitFormats")
    default List<String> splitFormats(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.asList(csv.split(","));
    }
}
