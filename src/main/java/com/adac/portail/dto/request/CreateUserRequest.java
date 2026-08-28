package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** POST /api/users/formateurs, POST /api/users/stagiaires — see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank
    @Size(max = 255)
    private String nom;

    @NotBlank
    @Size(max = 255)
    private String prenom;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /**
     * Only used for POST /api/users/stagiaires (formations to enroll in immediately); ignored
     * for POST /api/users/formateurs, which has no such field in docs/tech.md.
     */
    private List<Long> formationIds;
}
