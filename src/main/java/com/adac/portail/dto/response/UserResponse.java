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

    // Field named "active", not "isActive": Jackson serializes a boolean getter isXxx() by
    // stripping "is", so a field actually named "isActive" would serialize as "active" on the
    // wire — silently breaking the docs/tech.md "isActive" contract. @JsonProperty pins the key;
    // this also makes MapStruct's property-name matching line up with User.isActive() (see
    // UserMapper) without an explicit @Mapping.
    @JsonProperty("isActive")
    private boolean active;

    private boolean emailNotificationsEnabled;
    private OffsetDateTime createdAt;
}
