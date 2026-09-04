package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    // Bounded on purpose: this value becomes half of LoginAttemptService's lockout map key
    // before any DB lookup happens, so an unbounded string here is an easy way to grow that map
    // with oversized entries (see TICKET-045 review).
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    private String password;
}
