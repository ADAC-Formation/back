package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    private List<Long> formationIds;
}
