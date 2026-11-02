package com.adac.portail.mapper;

import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.Inscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, FormationMapper.class})
public interface InscriptionMapper {

    @Mapping(target = "formation.inscriptionsCount", ignore = true)
    InscriptionResponse toResponse(Inscription inscription);

    default InscriptionResponse toResponse(Inscription inscription, int formationInscriptionsCount) {
        InscriptionResponse response = toResponse(inscription);
        response.getFormation().setInscriptionsCount(formationInscriptionsCount);
        return response;
    }
}
