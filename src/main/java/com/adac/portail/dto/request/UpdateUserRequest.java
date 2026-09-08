package com.adac.portail.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PUT /api/users/{id} (TICKET-050) — SUPER_ADMIN editing any account's data. All fields
 * optional/nullable for a partial update, same semantics as {@code PUT /api/formations/{id}} — a
 * {@code null} field is left unchanged, distinct from an empty string. No {@code @NotBlank}
 * here for that reason (unlike {@link CreateUserRequest}, where every field is mandatory at
 * creation time).
 *
 * <p>Deliberately excludes {@code role} and {@code isActive} — the former never changes after
 * creation (see {@code UserServiceImpl.updateUser}'s Javadoc), the latter stays on the dedicated
 * {@code deactivate}/{@code reactivate} endpoints.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    // min = 1, not just @NotBlank's absence: null still means "leave unchanged" (see class
    // Javadoc), but a present, blank value ("") must not silently overwrite the NOT NULL column
    // — same convention as UpdateFormationRequest.intitule (branch-wide review: @Email alone
    // returns valid for "", and @Size alone only checks length when non-null).
    @Size(min = 1, max = 255)
    private String nom;

    @Size(min = 1, max = 255)
    private String prenom;

    @Email
    @Size(min = 1, max = 255)
    private String email;
}
