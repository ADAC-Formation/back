package com.adac.portail.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * POST /api/messages/send — single endpoint, two body shapes (see docs/tech.md): individual
 * ({@code recipientIds} set, {@code filter} null) or group ({@code filter} set, {@code
 * recipientIds} null). Both DTOs can't bind to one {@code @RequestBody} parameter, so this is
 * one class covering both shapes; the service layer (TICKET-030) enforces "exactly one of
 * recipientIds/filter is set", the same way it already has to for {@code Filter.formationId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank
    @Size(max = 5000)
    private String content;

    /** Set for an individual message; null for a group message (use {@code filter} instead). */
    private List<Long> recipientIds;

    /** Set for a group message; null for an individual message (use {@code recipientIds} instead). */
    @Valid
    private Filter filter;

    /**
     * {@code formationId} is required only when {@code type == FORMATION} — that cross-field
     * rule isn't expressible with plain Bean Validation, so the service layer (TICKET-030)
     * validates it and returns 400 rather than a constraint annotation here.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {

        @NotNull
        private MessageFilterType type;

        private Long formationId;
    }
}
