package com.adac.portail.dto.request;

import com.adac.portail.entity.enums.Modalite;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateFormationRequest {

    @NotBlank
    @Size(max = 255)
    private String intitule;

    private String description;

    @NotNull
    private LocalDate dateDebut;

    @NotNull
    private LocalDate dateFin;

    @NotNull
    private Modalite modalite;

    @NotNull
    private Long categoryId;

    private Long formateurId;

    @AssertTrue(message = "dateFin must be on or after dateDebut")
    public boolean isDateRangeValid() {
        return dateDebut == null || dateFin == null || !dateFin.isBefore(dateDebut);
    }
}
