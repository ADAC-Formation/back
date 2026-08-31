package com.adac.portail.dto.response;

import com.adac.portail.entity.enums.EntityType;
import com.adac.portail.entity.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;

    /** Human-readable text, e.g. "Nouveau message de Marie". */
    private String content;

    private EntityType entityType;

    /** For navigation, e.g. click -> formation 5. */
    private Long entityId;

    // See UserResponse.active for why this isn't named isRead.
    @JsonProperty("isRead")
    private boolean read;

    private OffsetDateTime createdAt;
}
