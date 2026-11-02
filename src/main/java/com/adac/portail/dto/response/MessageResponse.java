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

    @Builder.Default
    private List<UserResponse> recipients = List.of();

    @JsonProperty("isGroup")
    private boolean group;

    private OffsetDateTime readAt;

    private OffsetDateTime createdAt;
}
