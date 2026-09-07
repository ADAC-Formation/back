package com.adac.portail.controller;

import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UnreadNotificationsResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notifications (US-015) — see docs/tech.md § 8. Every notification is scoped to the
 * caller (their own {@code recipient_id}) — no {@code @PreAuthorize} needed, there's no
 * cross-user access to gate at all, not even for SUPER_ADMIN (notifications are personal).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Full notification history", description = "Every notification of the caller, most recent first — includes read and bell-dismissed ones (only the bell view excludes them).")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class))))
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestParam(required = false) Boolean read,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(notificationService.getNotifications(principal, read));
    }

    @Operation(summary = "Bell dropdown payload", description = "Unread and not dismissed from the bell, most recent first. Polled by the frontend every 30-60s (docs/tech.md).")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UnreadNotificationsResponse.class)))
    @GetMapping("/unread")
    public ResponseEntity<UnreadNotificationsResponse> getUnread(@AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(notificationService.getUnread(principal));
    }

    @Operation(summary = "Mark a notification as read", description = "Idempotent.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = NotificationResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such notification, or it isn't the caller's",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id, @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(notificationService.markAsRead(principal, id));
    }

    @Operation(summary = "Mark all notifications as read")
    @ApiResponse(responseCode = "200", description = "OK")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AdacUserDetails principal) {
        notificationService.markAllAsRead(principal);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Dismiss from the bell", description = "Hides the notification from the bell dropdown (deletedFromBell = true) without removing it from the full history. Idempotent.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiResponse(responseCode = "404", description = "No such notification, or it isn't the caller's",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFromBell(@PathVariable Long id, @AuthenticationPrincipal AdacUserDetails principal) {
        notificationService.deleteFromBell(principal, id);
        return ResponseEntity.noContent().build();
    }
}
