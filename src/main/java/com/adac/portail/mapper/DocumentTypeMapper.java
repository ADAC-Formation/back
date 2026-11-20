package com.adac.portail.mapper;

import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.DocumentType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", uses = CategoryMapper.class)
public interface DocumentTypeMapper {

    @Mapping(target = "allowedFormats", source = "allowedFormats", qualifiedByName = "splitFormats")
    DocumentTypeResponse toResponse(DocumentType documentType);

    @Named("splitFormats")
    default List<String> splitFormats(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.asList(csv.split(","));
    }

    /** Un ensemble n'a pas d'ordre : les catégories sortent triées par nom pour une réponse stable. */
    @AfterMapping
    default void sortCategoriesByName(@MappingTarget DocumentTypeResponse response) {
        if (response.getCategories() != null) {
            response.setCategories(response.getCategories().stream()
                    .sorted(Comparator.comparing(CategoryResponse::getNom, String.CASE_INSENSITIVE_ORDER))
                    .toList());
        }
    }
}
