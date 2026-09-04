package com.adac.portail.controller;

import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout and "who am I". Login itself is handled directly by
 * {@link com.adac.portail.security.filter.JwtAuthenticationFilter} (see ARCHI.md) — there is no
 * {@code login} method here.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtCookieFactory jwtCookieFactory;

    @Operation(summary = "Logout", description = "Expires the jwt cookie.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdacUserDetails principal) {
        // Client-side only: this expires the cookie but the JWT itself stays valid for the rest
        // of its lifetime (JWT_EXPIRATION) if a copy of it survives elsewhere — no server-side
        // revocation exists yet. Accepted risk for the MVP given HttpOnly + SameSite=Strict, see
        // ARCHI.md — Authentification.
        log.info("Logout for {}", principal != null ? principal.getUsername() : "anonymous");

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieFactory.expire().toString())
                .build();
    }

    @Operation(summary = "Current user", description = "Returns the authenticated user (from the jwt cookie).")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AdacUserDetails principal) {
        // Defense in depth: /api/auth/** is currently permitAll at the filter-chain level
        // (SecurityConfig, tightened by TICKET-045), so this can be reached without a valid
        // cookie — the null check below is what makes docs/tech.md's "401 non authentifié"
        // contract hold regardless.
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Authentification requise"));
        }
        return ResponseEntity.ok(authService.getCurrentUser(principal));
    }
}
