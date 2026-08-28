package com.adac.portail.dto.response;

import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormationResponse {

    private Long id;
    private String intitule;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Modalite modalite;
    private FormationStatus status;

    /** May be the Super Admin (see docs/tech.md). */
    private UserResponse formateur;

    /** Computed — not derivable from the (deliberately lean) Formation entity alone; set by the service layer. */
    private int inscriptionsCount;

    private OffsetDateTime createdAt;
}
