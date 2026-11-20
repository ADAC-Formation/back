package com.adac.portail.dto.request;

import com.adac.portail.entity.enums.DeadlineAnchor;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Corps de POST et PUT /api/document-types : le PUT remplace tous les champs. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeRequest {

    @NotBlank
    @Size(max = 255)
    private String nom;

    @Size(max = 500)
    private String aide;

    @NotNull
    @JsonProperty("isRequired")
    private Boolean required;

    @NotNull
    @JsonProperty("isSensitive")
    private Boolean sensitive;

    @NotNull
    private Boolean appliesToAllFormations;

    @Size(max = 100)
    private List<@NotNull Long> categoryIds;

    @Min(0)
    @Max(365)
    private Integer deadlineDays;

    private DeadlineAnchor deadlineAnchor;

    // 6 formats existent ; la marge laisse passer des doublons que le service écarte.
    @Size(max = 20)
    private List<@NotNull String> allowedFormats;
}
