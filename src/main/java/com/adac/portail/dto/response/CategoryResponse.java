package com.adac.portail.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CategoryResponse {

    private Long id;
    private String nom;
    private String couleur;

    // Field named "active", not "isActive" — same reasoning as UserResponse.active: Jackson would
    // otherwise serialize a field literally named "isActive" as "active" on the wire (it strips
    // "is" from the isXxx() getter it generates for it), breaking docs/tech.md's "isActive"
    // contract. @JsonProperty pins the wire name; this also keeps MapStruct's property-name
    // matching aligned with Category.isActive() (see CategoryMapper) with no explicit @Mapping.
    @JsonProperty("isActive")
    private boolean active;

    private OffsetDateTime createdAt;
}
