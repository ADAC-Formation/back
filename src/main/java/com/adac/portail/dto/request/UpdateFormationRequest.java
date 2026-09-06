package com.adac.portail.dto.request;

import com.adac.portail.entity.enums.Modalite;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * PUT /api/formations/{id} — see docs/tech.md. Every field is nullable: {@code null} means
 * "leave unchanged" ({@link com.adac.portail.service.FormationServiceImpl#updateFormation}
 * applies only the fields actually present), not "clear this field" — there's no documented way
 * to unset {@code description} or reassign the auto-assigned Super Admin back once a
 * {@code formateurId} was set, and the ticket doesn't test either.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFormationRequest {

    // min = 1, not just @NotBlank's absence: null still means "leave unchanged" (see class
    // Javadoc), but a present, blank value ("") must not silently overwrite the NOT NULL column
    // with an empty title (review) — @Size alone only checks length when non-null, so min = 1
    // rejects "" while still letting null through.
    @Size(min = 1, max = 255)
    private String intitule;

    private String description;

    private LocalDate dateDebut;

    private LocalDate dateFin;

    private Modalite modalite;

    private Long categoryId;

    private Long formateurId;
}
