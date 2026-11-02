package com.adac.portail.dto.request;

import com.adac.portail.entity.enums.Modalite;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFormationRequest {

    @Size(min = 1, max = 255)
    private String intitule;

    private String description;

    private LocalDate dateDebut;

    private LocalDate dateFin;

    private Modalite modalite;

    private Long categoryId;

    private Long formateurId;
}
