package com.adac.portail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PUT /api/categories/{id} — same fields as {@link CreateCategoryRequest}, see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @NotBlank
    @Size(max = 255)
    private String nom;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "couleur must be a hex color like #RRGGBB")
    private String couleur;
}
