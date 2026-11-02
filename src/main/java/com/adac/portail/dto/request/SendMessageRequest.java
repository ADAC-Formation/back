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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank
    @Size(max = 5000)
    private String content;

    private List<@NotNull Long> recipientIds;

    @Valid
    private Filter filter;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {

        @NotNull
        private MessageFilterType type;

        private Long formationId;

        private List<@NotNull Long> userIds;
    }
}
