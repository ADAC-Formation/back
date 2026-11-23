package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackForm;
import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackScreenshot;
import com.adac.portail.dto.request.FeedbackType;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.FeedbackDeliveryException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.utils.FeedbackScreenshotValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    private static final String VALID_MESSAGE = "Le bouton Enregistrer ne répond pas";

    @Mock
    private EmailService emailService;

    @Mock
    private FeedbackRateLimiter rateLimiter;

    @Mock
    private FeedbackScreenshotValidator screenshotValidator;

    private FeedbackServiceImpl service;
    private User author;

    @BeforeEach
    void setUp() {
        service = new FeedbackServiceImpl(emailService, rateLimiter, screenshotValidator);
        author = User.builder().id(5L).email("formateur@adac.fr").prenom("Paul").nom("Martin")
                .role(Role.ADMIN).build();
        lenient().when(screenshotValidator.validate(any())).thenReturn(Optional.empty());
    }

    private static FeedbackForm form(String type, String message) {
        return new FeedbackForm(type, message, "/formations/12", "Mozilla/5.0", "1280x720", "0.0.0+local");
    }

    @Test
    void sendsTheFeedbackAsTheSessionUserThenRecordsIt() {
        service.send(author, form("BUG", VALID_MESSAGE), null);

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        InOrder order = inOrder(rateLimiter, emailService);
        order.verify(rateLimiter).checkAllowed(5L);
        order.verify(emailService).sendFeedbackEmail(eq(author), captor.capture(), isNull());
        order.verify(rateLimiter).record(5L);
        assertThat(captor.getValue()).isEqualTo(new FeedbackRequest(FeedbackType.BUG, VALID_MESSAGE,
                "/formations/12", "Mozilla/5.0", "1280x720", "0.0.0+local"));
    }

    @Test
    void acceptsSuggestionInAnyCase() {
        service.send(author, form("suggestion", VALID_MESSAGE), null);

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(emailService).sendFeedbackEmail(eq(author), captor.capture(), isNull());
        assertThat(captor.getValue().type()).isEqualTo(FeedbackType.SUGGESTION);
    }

    @Test
    void rejectsMissingOrUnknownType() {
        assertThatThrownBy(() -> service.send(author, form(null, VALID_MESSAGE), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Type de retour invalide (Bug ou Suggestion)");
        assertThatThrownBy(() -> service.send(author, form("AUTRE", VALID_MESSAGE), null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void rejectsMessageShorterThanTenCharactersIgnoringSurroundingSpaces() {
        assertThatThrownBy(() -> service.send(author, form("BUG", "   court   "), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le message doit contenir entre 10 et 2000 caractères");
        assertThatThrownBy(() -> service.send(author, form("BUG", null), null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void acceptsTenAndTwoThousandCharactersButNotMore() {
        service.send(author, form("BUG", "a".repeat(10)), null);
        service.send(author, form("BUG", "a".repeat(2000)), null);

        assertThatThrownBy(() -> service.send(author, form("BUG", "a".repeat(2001)), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void truncatesOverlongContextFieldsTo500Characters() {
        FeedbackForm longContext = new FeedbackForm("BUG", VALID_MESSAGE, "/" + "p".repeat(800),
                "u".repeat(800), null, "v".repeat(800));

        service.send(author, longContext, null);

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(emailService).sendFeedbackEmail(eq(author), captor.capture(), isNull());
        assertThat(captor.getValue().page()).hasSize(500);
        assertThat(captor.getValue().userAgent()).hasSize(500);
        assertThat(captor.getValue().viewport()).isEmpty();
        assertThat(captor.getValue().appVersion()).hasSize(500);
    }

    @Test
    void attachesTheValidatedScreenshotWithItsDetectedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("screenshot", "x.png", "image/png", new byte[] {1, 2, 3});
        when(screenshotValidator.validate(file)).thenReturn(Optional.of("image/png"));

        service.send(author, form("BUG", VALID_MESSAGE), file);

        ArgumentCaptor<FeedbackScreenshot> captor = ArgumentCaptor.forClass(FeedbackScreenshot.class);
        verify(emailService).sendFeedbackEmail(eq(author), any(), captor.capture());
        assertThat(captor.getValue().content()).containsExactly(1, 2, 3);
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void invalidScreenshotSendsNothing() {
        MockMultipartFile file = new MockMultipartFile("screenshot", "x.pdf", "image/png", new byte[] {0x25});
        when(screenshotValidator.validate(file)).thenThrow(new BadRequestException("Format d'image non autorisé"));

        assertThatThrownBy(() -> service.send(author, form("BUG", VALID_MESSAGE), file))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(emailService);
        verify(rateLimiter, never()).record(any());
    }

    @Test
    void rateLimitedUserSendsNothing() {
        doThrow(new RateLimitException("Trop de retours")).when(rateLimiter).checkAllowed(5L);

        assertThatThrownBy(() -> service.send(author, form("BUG", VALID_MESSAGE), null))
                .isInstanceOf(RateLimitException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void mailFailureBecomesDeliveryExceptionAndDoesNotConsumeTheQuota() {
        doThrow(new MailSendException("SMTP down")).when(emailService).sendFeedbackEmail(any(), any(), any());

        assertThatThrownBy(() -> service.send(author, form("BUG", VALID_MESSAGE), null))
                .isInstanceOf(FeedbackDeliveryException.class)
                .hasMessage("Envoi impossible pour le moment, réessayez plus tard.");
        verify(rateLimiter, never()).record(any());
    }
}
