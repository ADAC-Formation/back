package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/auth/reset-password — same shape as {@link ActivateAccountRequest}, kept as a
 * separate type since the two flows (first activation vs. later password reset) are free to
 * diverge — see docs/tech.md. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "code must be 6 digits")
    private String code;

    // See ActivateAccountRequest.newPassword for why both bounds and the pattern are here.
    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d)[\\s\\S]*", message = "must contain at least one uppercase letter and one digit")
    private String newPassword;
}
