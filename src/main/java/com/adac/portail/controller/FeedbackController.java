package com.adac.portail.controller;

import com.adac.portail.dto.request.FeedbackForm;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Tag(name = "Feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "Send a bug report or suggestion to the developer",
            description = "multipart/form-data. SUPER_ADMIN and ADMIN only. Emails the configured recipient "
                    + "(FEEDBACK_RECIPIENT) with the author as Reply-To; nothing is stored. "
                    + "Parts: type (BUG|SUGGESTION), message (10-2000 chars), page, userAgent, viewport, "
                    + "appVersion, optional screenshot (png/jpeg/webp, 3 MB max).")
    @ApiResponse(responseCode = "201", description = "Email sent")
    @ApiResponse(responseCode = "400", description = "Invalid type, message length, or screenshot",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "STAGIAIRE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "More than 10 feedbacks in the last hour",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Email could not be sent",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> sendFeedback(
            @ModelAttribute FeedbackForm form,
            @RequestPart(name = "screenshot", required = false) MultipartFile screenshot,
            @AuthenticationPrincipal AdacUserDetails principal) {
        feedbackService.send(principal.getUser(), form, screenshot);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
