package com.adac.portail.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/formations/{id}/inscriptions — see docs/tech.md. Named {@code stagiaireId} (not
 * {@code userId} as first sketched in docs/tickets/TICKET-023.md), matching the field
 * docs/tech.md § 5 actually documents — Manon's only source for this contract.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InscriptionRequest {

    @NotNull
    private Long stagiaireId;
}
