package com.adac.portail.mapper;

import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Formation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, CategoryMapper.class})
public interface FormationMapper {

    @Mapping(target = "inscriptionsCount", ignore = true)
    FormationResponse toResponse(Formation formation);

    default FormationResponse toResponse(Formation formation, int inscriptionsCount) {
        FormationResponse response = toResponse(formation);
        response.setInscriptionsCount(inscriptionsCount);
        return response;
    }
}
