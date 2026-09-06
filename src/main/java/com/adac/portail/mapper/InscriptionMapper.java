package com.adac.portail.mapper;

import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.Inscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, FormationMapper.class})
public interface InscriptionMapper {

    /**
     * {@code formation.inscriptionsCount} ignored here (default 0) rather than left to
     * {@code FormationMapper}'s own default — TICKET-023 review: every caller of this interface
     * has an accurate count on hand for free (see {@link #toResponse(Inscription, int)}), so a
     * plain {@code toResponse(Inscription)} that silently reports 0 would contradict docs/tech.md
     * § 5 ("formation.inscriptionsCount : calculé") instead of just omitting it.
     */
    @Mapping(target = "formation.inscriptionsCount", ignore = true)
    InscriptionResponse toResponse(Inscription inscription);

    /**
     * @param formationInscriptionsCount the enrollment count for {@code inscription.formation} —
     *                                   callers already have this for free: every row in a
     *                                   {@code GET .../inscriptions} response shares the same
     *                                   formation and the same count as the list's own size, and
     *                                   {@code POST .../inscriptions} just changed it by exactly
     *                                   one (see {@code InscriptionServiceImpl}).
     */
    default InscriptionResponse toResponse(Inscription inscription, int formationInscriptionsCount) {
        InscriptionResponse response = toResponse(inscription);
        response.getFormation().setInscriptionsCount(formationInscriptionsCount);
        return response;
    }
}
