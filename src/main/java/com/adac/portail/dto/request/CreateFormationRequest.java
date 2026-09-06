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

/** POST /api/formations — see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateFormationRequest {

    @NotBlank
    @Size(max = 255)
    private String intitule;

    /** Optional, nullable. */
    private String description;

    @NotNull
    private LocalDate dateDebut;

    @NotNull
    private LocalDate dateFin;

    @NotNull
    private Modalite modalite;

    /** Obligatoire — see docs/tech.md § 4, "400 — categoryId manquant ou introuvable". */
    @NotNull
    private Long categoryId;

    /** Nullable — null means the Super Admin is auto-assigned. */
    private Long formateurId;

    /**
     * Mirrors the DB's {@code chk_formations_date_order} CHECK constraint at the validation
     * layer, so an inverted range returns 400 (per docs/tech.md) instead of surfacing as a
     * 500 from a constraint violation at insert time.
     */
    @AssertTrue(message = "dateFin must be on or after dateDebut")
    public boolean isDateRangeValid() {
        return dateDebut == null || dateFin == null || !dateFin.isBefore(dateDebut);
    }
}
