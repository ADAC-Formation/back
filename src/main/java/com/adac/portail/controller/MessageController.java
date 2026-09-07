package com.adac.portail.controller;

import com.adac.portail.dto.request.MessageFilterType;
import com.adac.portail.dto.request.SendMessageRequest;
import com.adac.portail.dto.response.ConversationResponse;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
 * Individual messaging (US-013) — see docs/tech.md § 7. No {@code @PreAuthorize} on any route
 * here: every authenticated role may list/read/send/mark-read (docs/ARCHI.md endpoint table —
 * "Tous"), and the actual per-role recipient restrictions are data-dependent (who the target user
 * is), not expressible as a static route rule — {@link MessageService} enforces those and throws
 * {@link com.adac.portail.exception.UnauthorizedException} (403) when they're violated.
 *
 * <p>{@code @Validated} (TICKET-030, branch-wide review): {@code previewGroupRecipients} builds a
 * {@code SendMessageRequest.Filter} by hand from query params instead of binding a {@code @Valid}
 * request body, so the container-element guard on {@code Filter.userIds} (see its Javadoc) only
 * runs here if the class itself opts into validating {@code @RequestParam}s.</p>
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
@Tag(name = "Messages")
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "List conversations", description = "One entry per correspondent, most recently active first.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(messageService.getConversations(principal));
    }

    @Operation(summary = "Get a conversation thread", description = "conversationId is the other participant's user id — there is no dedicated Conversation entity.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getConversationMessages(
            @PathVariable @Parameter(description = "The other participant's user id") Long conversationId,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.ok(messageService.getConversationMessages(principal, conversationId));
    }

    @Operation(summary = "Send a message", description = "Individual (recipientIds, exactly one) or group (filter — TICKET-030). Exactly one of the two must be set.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Neither/both of recipientIds and filter set, recipientIds not exactly one id, filter missing a required field, or the filter resolved zero recipients",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The caller's role may not message this recipient, or may not use this filter/formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such recipient, formation, or one of filter.userIds",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/send")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal AdacUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(principal, request));
    }

    @Operation(summary = "Preview group message recipients", description = "Resolves who a group send would reach, without sending anything (US-014 AC-03). Same role/filter rules as POST /send's group branch.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class))))
    @ApiResponse(responseCode = "400", description = "Filter missing a required field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The caller's role may not use this filter/formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such formation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/group/preview")
    public ResponseEntity<List<UserResponse>> previewGroupRecipients(
            @RequestParam MessageFilterType filterType,
            @RequestParam(required = false) Long formationId,
            @RequestParam(required = false) List<@NotNull Long> userIds,
            @AuthenticationPrincipal AdacUserDetails principal) {
        SendMessageRequest.Filter filter = new SendMessageRequest.Filter(filterType, formationId, userIds);
        return ResponseEntity.ok(messageService.previewGroupRecipients(principal, filter));
    }

    @Operation(summary = "Mark a message as read", description = "Marks the single message `id`, not the whole conversation.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "No such message, or the caller isn't one of its recipients",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, @AuthenticationPrincipal AdacUserDetails principal) {
        messageService.markAsRead(principal, id);
        return ResponseEntity.ok().build();
    }
}
