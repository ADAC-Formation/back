package com.adac.portail.mapper;

import com.adac.portail.dto.response.UserInscriptionResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {InscriptionMapperImpl.class, FormationMapperImpl.class, UserMapperImpl.class, CategoryMapperImpl.class})
class InscriptionMapperTest {

    @Autowired
    private InscriptionMapper inscriptionMapper;

    @Test
    void userInscriptionCarriesIdAndFormation() {
        Inscription inscription = Inscription.builder().id(5L).formation(Formation.builder().id(2L).build()).build();

        UserInscriptionResponse response = inscriptionMapper.toUserInscription(inscription);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getFormation().getId()).isEqualTo(2L);
    }
}
