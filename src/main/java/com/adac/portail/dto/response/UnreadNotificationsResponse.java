package com.adac.portail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code GET /api/notifications/unread} (docs/tech.md § 8) — the bell dropdown's payload. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadNotificationsResponse {

    private int count;

    @Builder.Default
    private List<NotificationResponse> notifications = List.of();
}
