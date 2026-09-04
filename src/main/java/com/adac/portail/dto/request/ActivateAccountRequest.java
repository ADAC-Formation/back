package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/auth/activate — see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivateAccountRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "code must be 6 digits")
    private String code;

    // 72: BCrypt silently ignores bytes past its 72-byte input limit — capping here means what
    // gets validated is exactly what gets hashed, instead of a longer password that's accepted
    // but has its tail truncated away. Uppercase + digit per docs/STORIES.md US-002 AC-03 —
    // "validé côté client ET serveur", so this can't be client-only.
    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d)[\\s\\S]*", message = "must contain at least one uppercase letter and one digit")
    private String newPassword;
}
