package com.adac.portail.mapper;

import com.adac.portail.dto.response.DocumentResponse;
import com.adac.portail.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface DocumentMapper {

    @Mapping(target = "formationId", source = "formation.id")
    @Mapping(target = "inscriptionId", source = "inscription.id")
    DocumentResponse toResponse(Document document);
}
