package com.adac.portail.dto.response;

import com.adac.portail.entity.enums.Role;
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
public class UserResponse {

    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private Role role;

    @JsonProperty("isActive")
    private boolean active;

    private boolean emailNotificationsEnabled;
    private OffsetDateTime createdAt;
}
