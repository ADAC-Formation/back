package com.adac.portail.mapper;

import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {FormationMapperImpl.class, UserMapperImpl.class, CategoryMapperImpl.class})
class FormationMapperTest {

    @Autowired
    private FormationMapper formationMapper;

    @Test
    void formateurAlwaysCarriesItsRole() {
        User formateur = User.builder().id(7L).email("f@adac.fr").nom("Durand").prenom("Paul")
                .role(Role.ADMIN).build();

        FormationResponse response = formationMapper.toResponse(Formation.builder().id(1L).formateur(formateur).build(), 3);

        assertThat(response.getFormateur().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void nestedFormateurIsActivatedWhenActive() {
        User formateur = User.builder().id(7L).role(Role.ADMIN).isActive(true).build();

        FormationResponse response = formationMapper.toResponse(Formation.builder().id(1L).formateur(formateur).build(), 0);

        assertThat(response.getFormateur().isActivated()).isTrue();
    }
}
