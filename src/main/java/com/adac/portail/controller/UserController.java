package com.adac.portail.controller;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Formateur/stagiaire account management (US-007, US-008) — see docs/tech.md § 2.
 *
 * <p>Role checks are {@code @PreAuthorize} (method security, {@code SecurityConfig
 * .@EnableMethodSecurity}) rather than URL rules in {@code SecurityConfig}, unlike
 * {@code AuthController}'s auth endpoints — every route here needs at least one authenticated
 * role and several need a role-dependent response body (see {@code UserServiceImpl}), so the
 * per-route granularity of method security fits better than one more block of
 * {@code requestMatchers}.</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create formateur", description = "SUPER_ADMIN only. Triggers the activation email.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/formateurs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> createFormateur(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createFormateur(request));
    }

    @Operation(summary = "Create stagiaire", description = "SUPER_ADMIN only. Enrolls in formationIds and triggers the activation email.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "403", description = "Insufficient role",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/stagiaires")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> createStagiaire(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createStagiaire(request));
    }

    @Operation(summary = "List formateurs", description = "SUPER_ADMIN sees all; ADMIN sees active only regardless of the active param.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/formateurs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<UserResponse>> getFormateurs(
            @RequestParam(required = false) Boolean active,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(userService.getFormateurs(active, principal));
    }

    @Operation(summary = "List stagiaires", description = "SUPER_ADMIN sees all (optionally filtered); ADMIN sees only active stagiaires of their own formations.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/stagiaires")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<UserResponse>> getStagiaires(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long formationId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(userService.getStagiaires(active, formationId, principal));
    }

    @Operation(summary = "Get user profile", description = "SUPER_ADMIN and ADMIN only — a STAGIAIRE reads their own profile via GET /api/auth/me instead. An ADMIN caller only sees a stagiaire profile if enrolled in one of their formations (same rule as GET /api/users/stagiaires); a 404 either way.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such user, or an ADMIN caller has no formation in common with this stagiaire",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> getById(
            @PathVariable @Parameter(description = "User id") Long id,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(userService.getById(id, principal));
    }

    @Operation(summary = "Suspend a user", description = "SUPER_ADMIN only. The user can no longer log in. Refuses self-suspension and suspending the last active SUPER_ADMIN.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Self-suspension, or this is the last active SUPER_ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long id, @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(userService.deactivate(id, principal));
    }

    @Operation(summary = "Reactivate a suspended user", description = "SUPER_ADMIN only. Rejects a user who was never activated in the first place — see docs/ARCHI.md.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "This user was never activated — use resend-activation instead",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.reactivate(id));
    }
}
