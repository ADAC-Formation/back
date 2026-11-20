package com.adac.portail.dto.response;

import com.adac.portail.entity.enums.DeadlineAnchor;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentTypeResponse {

    private Long id;
    private String nom;
    private String aide;

    @JsonProperty("isRequired")
    private boolean required;

    @JsonProperty("isSensitive")
    private boolean sensitive;

    private boolean appliesToAllFormations;

    private List<CategoryResponse> categories;

    private Integer deadlineDays;
    private DeadlineAnchor deadlineAnchor;

    /** Vide = règle générale (tous les formats autorisés par le dépôt de documents). */
    private List<String> allowedFormats;

    @JsonProperty("isActive")
    private boolean active;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
