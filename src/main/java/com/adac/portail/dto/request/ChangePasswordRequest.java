package com.adac.portail.dto.request;

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
public class ChangePasswordRequest {

    @NotBlank
    @Size(max = 72)
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d)[\\s\\S]*", message = "must contain at least one uppercase letter and one digit")
    private String newPassword;
}
