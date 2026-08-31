package com.adac.portail.mapper;

import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Formation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface FormationMapper {

    /**
     * {@code inscriptionsCount} isn't derivable from the (deliberately lean, no back-reference
     * collection) Formation entity — it needs a count query the mapper doesn't have access to.
     * Use {@link #toResponse(Formation, int)} once that count is known (e.g. from
     * InscriptionRepository in the service layer).
     */
    @Mapping(target = "inscriptionsCount", ignore = true)
    FormationResponse toResponse(Formation formation);

    default FormationResponse toResponse(Formation formation, int inscriptionsCount) {
        FormationResponse response = toResponse(formation);
        response.setInscriptionsCount(inscriptionsCount);
        return response;
    }
}
