package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/auth/resend-activation — see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResendActivationRequest {

    @NotBlank
    @Email
    private String email;
}
