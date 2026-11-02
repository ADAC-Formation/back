package com.adac.portail.controller;

import com.adac.portail.dto.request.ActivateAccountRequest;
import com.adac.portail.dto.request.ForgotPasswordRequest;
import com.adac.portail.dto.request.ResendActivationRequest;
import com.adac.portail.dto.request.ResetPasswordRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.StatusMessageResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.service.ActivationService;
import com.adac.portail.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final ActivationService activationService;
    private final JwtCookieFactory jwtCookieFactory;

    @Operation(summary = "Logout", description = "Expires the jwt cookie.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdacUserDetails principal) {

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

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Authentification requise"));
        }
        return ResponseEntity.ok(authService.getCurrentUser(principal));
    }

    @Operation(summary = "Activate account", description = "First login after admin account creation: consumes the emailed code and sets a new password.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Account activated",
            content = @Content(schema = @Schema(implementation = StatusMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body, or invalid/expired/guess-exhausted code — all three are indistinguishable on purpose, see ActivationServiceImpl.verifyAndConsumeToken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/activate")
    public ResponseEntity<StatusMessageResponse> activate(@Valid @RequestBody ActivateAccountRequest request) {
        activationService.activate(request);
        return ResponseEntity.ok(new StatusMessageResponse("Compte activé avec succès"));
    }

    @Operation(summary = "Resend activation code", description = "Issues a fresh activation code, rate-limited.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Code sent (or email unknown/already active/suspended — same response either way)",
            content = @Content(schema = @Schema(implementation = StatusMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Too many codes requested recently for this (known, pending) account",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/resend-activation")
    public ResponseEntity<StatusMessageResponse> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        activationService.resendActivation(request.getEmail());
        return ResponseEntity.ok(new StatusMessageResponse("Si cet email existe, un code vous a été envoyé."));
    }

    @Operation(summary = "Forgot password", description = "Sends a password reset code — same response whether the email is known or not.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Same response every time, by design — known email, unknown email, or currently rate-limited",
            content = @Content(schema = @Schema(implementation = StatusMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/forgot-password")
    public ResponseEntity<StatusMessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        activationService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new StatusMessageResponse("Si cet email existe, un code vous a été envoyé."));
    }

    @Operation(summary = "Reset password", description = "Consumes the emailed reset code and sets a new password.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Password updated",
            content = @Content(schema = @Schema(implementation = StatusMessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid body, or invalid/expired/guess-exhausted code — all three are indistinguishable on purpose, see ActivationServiceImpl.verifyAndConsumeToken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/reset-password")
    public ResponseEntity<StatusMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        activationService.resetPassword(request);
        return ResponseEntity.ok(new StatusMessageResponse("Mot de passe mis à jour avec succès"));
    }
}
