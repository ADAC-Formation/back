package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d)[\\s\\S]*", message = "must contain at least one uppercase letter and one digit")
    private String newPassword;
}
