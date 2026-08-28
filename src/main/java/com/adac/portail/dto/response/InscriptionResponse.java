package com.adac.portail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscriptionResponse {

    private Long id;
    private UserResponse stagiaire;
    private FormationResponse formation;
    private OffsetDateTime inscritLe;
}
