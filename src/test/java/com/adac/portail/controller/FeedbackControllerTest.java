package com.adac.portail.controller;

import com.adac.portail.dto.request.FeedbackForm;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.FeedbackDeliveryException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.WithMockAdacUser;
import com.adac.portail.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(FeedbackControllerTest.MethodSecurityTestConfig.class)
class FeedbackControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static MockMultipartHttpServletRequestBuilder validFeedback() {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/feedback")
                .param("type", "BUG")
                .param("message", "Le bouton Enregistrer ne répond pas")
                .param("page", "/formations/12")
                .param("userAgent", "Mozilla/5.0")
                .param("viewport", "1280x720")
                .param("appVersion", "0.0.0+local");
    }

    @Test
    @WithMockAdacUser(id = 3L, role = Role.SUPER_ADMIN, email = "chargee@adac.fr")
    void trainingOfficerSendsFeedbackAndGetsCreatedWithoutBody() throws Exception {
        mockMvc.perform(validFeedback())
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        ArgumentCaptor<User> author = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<FeedbackForm> form = ArgumentCaptor.forClass(FeedbackForm.class);
        verify(feedbackService).send(author.capture(), form.capture(), isNull());
        assertThat(author.getValue().getId()).isEqualTo(3L);
        assertThat(form.getValue()).isEqualTo(new FeedbackForm("BUG", "Le bouton Enregistrer ne répond pas",
                "/formations/12", "Mozilla/5.0", "1280x720", "0.0.0+local"));
    }

    @Test
    @WithMockAdacUser(role = Role.ADMIN)
    void trainerSendsFeedbackWithScreenshot() throws Exception {
        MockMultipartFile screenshot = new MockMultipartFile("screenshot", "capture.png", "image/png",
                new byte[] {(byte) 0x89, 0x50});

        mockMvc.perform(validFeedback().file(screenshot))
                .andExpect(status().isCreated());

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(feedbackService).send(any(), any(), file.capture());
        assertThat(file.getValue().getBytes()).containsExactly(0x89, 0x50);
    }

    @Test
    @WithMockAdacUser(role = Role.STAGIAIRE)
    void traineeIsForbidden() throws Exception {
        mockMvc.perform(validFeedback())
                .andExpect(status().isForbidden());

        verifyNoInteractions(feedbackService);
    }

    @Test
    @WithMockAdacUser(id = 3L, email = "chargee@adac.fr")
    void identityPartsSentByTheClientAreIgnored() throws Exception {
        mockMvc.perform(validFeedback().param("email", "pirate@example.org").param("role", "SUPER_ADMIN"))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> author = ArgumentCaptor.forClass(User.class);
        verify(feedbackService).send(author.capture(), any(), any());
        assertThat(author.getValue().getEmail()).isEqualTo("chargee@adac.fr");
    }

    @Test
    @WithMockAdacUser
    void invalidFeedbackReturnsBadRequest() throws Exception {
        doThrow(new BadRequestException("Type de retour invalide (Bug ou Suggestion)"))
                .when(feedbackService).send(any(), any(), any());

        mockMvc.perform(validFeedback())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Type de retour invalide (Bug ou Suggestion)"));
    }

    @Test
    @WithMockAdacUser
    void tooManyFeedbacksReturnsTooManyRequests() throws Exception {
        doThrow(new RateLimitException("Trop de retours envoyés. Réessayez dans une heure."))
                .when(feedbackService).send(any(), any(), any());

        mockMvc.perform(validFeedback())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Trop de retours envoyés. Réessayez dans une heure."));
    }

    @Test
    @WithMockAdacUser
    void mailFailureReturnsServiceUnavailable() throws Exception {
        doThrow(new FeedbackDeliveryException(new RuntimeException("SMTP down")))
                .when(feedbackService).send(any(), any(), any());

        mockMvc.perform(validFeedback())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Envoi impossible pour le moment, réessayez plus tard."));
    }
}
