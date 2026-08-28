package com.adac.portail.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private Long id;
    private String content;
    private UserResponse sender;

    /**
     * Not derivable from the Message entity alone (lives in MessageRecipient); set by the
     * service layer. Defaults to empty, never null, per docs/tech.md's "listes vides -> []"
     * convention — matters here because MessageMapper leaves this field untouched.
     */
    @Builder.Default
    private List<UserResponse> recipients = List.of();

    // See UserResponse.active for why this isn't named isGroup.
    @JsonProperty("isGroup")
    private boolean group;

    /**
     * Whether/when the *current viewer* read this message — per-recipient, so also set by the
     * service layer rather than mapped directly from the entity.
     */
    private OffsetDateTime readAt;

    private OffsetDateTime createdAt;
}
