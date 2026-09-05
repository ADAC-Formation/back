package com.adac.portail.dto;

import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.MessageResponse;
import com.adac.portail.dto.response.NotificationResponse;
import com.adac.portail.dto.response.UserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the docs/tech.md JSON contract for the "isXxx"-named boolean fields. Without the
 * {@code @JsonProperty} pin on each (see UserResponse.active and its siblings), Jackson strips
 * the "is" from an {@code isXxx()} getter and serializes as "xxx" instead — silently breaking
 * the frontend contract with no error anywhere. See TICKET-005 review.
 */
class BooleanFieldJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void userResponseSerializesIsActiveNotActive() throws Exception {
        UserResponse response = UserResponse.builder().active(true).emailNotificationsEnabled(true).build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isActive\":true");
        assertThat(json).doesNotContain("\"active\":");
    }

    @Test
    void messageResponseSerializesIsGroupNotGroup() throws Exception {
        MessageResponse response = MessageResponse.builder().group(true).build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isGroup\":true");
        assertThat(json).doesNotContain("\"group\":");
    }

    @Test
    void notificationResponseSerializesIsReadNotRead() throws Exception {
        NotificationResponse response = NotificationResponse.builder().read(true).build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isRead\":true");
        assertThat(json).doesNotContain("\"read\":");
    }

    @Test
    void categoryResponseSerializesIsActiveNotActive() throws Exception {
        CategoryResponse response = CategoryResponse.builder().active(true).build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isActive\":true");
        assertThat(json).doesNotContain("\"active\":");
    }
}
