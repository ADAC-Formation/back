package com.adac.portail.service;

import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackScreenshot;
import com.adac.portail.dto.request.FeedbackType;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.utils.EmailTemplateBuilder;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateBuilder templateBuilder;

    @InjectMocks
    private EmailServiceImpl emailService;

    private User user;

    @BeforeEach
    void setUp() {

        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@adac.fr");

        user = User.builder()
                .id(1L)
                .email("stagiaire@adac.fr")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .emailNotificationsEnabled(true)
                .build();
    }

    @Test
    void sendActivationEmailSendsCorrectlyAddressedHtmlMessage() throws Exception {
        when(templateBuilder.buildActivationEmail("Jane", "123456")).thenReturn("<html>code 123456</html>");

        emailService.sendActivationEmail(user, "123456");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Votre code d'activation ADAC");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("stagiaire@adac.fr");
        assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@adac.fr");
        assertThat(sent.getContentType()).containsIgnoringCase("text/html").containsIgnoringCase("UTF-8");
        assertThat((String) sent.getContent()).isEqualTo("<html>code 123456</html>");
    }

    @Test
    void sendActivationEmailAlwaysSendsEvenWithNotificationsDisabled() {
        user.setEmailNotificationsEnabled(false);
        when(templateBuilder.buildActivationEmail("Jane", "123456")).thenReturn("<html>code 123456</html>");

        emailService.sendActivationEmail(user, "123456");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordResetEmailSendsCorrectSubjectAndBody() throws Exception {
        when(templateBuilder.buildPasswordResetEmail("Jane", "654321")).thenReturn("<html>code 654321</html>");

        emailService.sendPasswordResetEmail(user, "654321");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Réinitialisation de votre mot de passe ADAC");
        assertThat((String) sent.getContent()).isEqualTo("<html>code 654321</html>");
    }

    @Test
    void sendPasswordResetEmailAlwaysSendsEvenWithNotificationsDisabled() {
        user.setEmailNotificationsEnabled(false);
        when(templateBuilder.buildPasswordResetEmail("Jane", "654321")).thenReturn("<html>code 654321</html>");

        emailService.sendPasswordResetEmail(user, "654321");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendActivationEmailPropagatesMailExceptionRatherThanSwallowingIt() {

        when(templateBuilder.buildActivationEmail(any(), any())).thenReturn("<html></html>");
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendActivationEmail(user, "123456"))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void sendActivationEmailWrapsAMalformedAddressAsMailPreparationException() {
        user.setEmail("not an address");
        when(templateBuilder.buildActivationEmail(any(), any())).thenReturn("<html></html>");

        assertThatThrownBy(() -> emailService.sendActivationEmail(user, "123456"))
                .isInstanceOf(MailPreparationException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendNotificationEmailWithToggleEnabledSendsTheEmail() throws Exception {
        when(templateBuilder.buildNotificationEmail("Nouveau message")).thenReturn("<html>Nouveau message</html>");

        emailService.sendNotificationEmail(user, "Nouveau message");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Nouvelle notification ADAC");
    }

    @Test
    void sendNotificationEmailWithToggleDisabledSendsNothing() {
        user.setEmailNotificationsEnabled(false);

        emailService.sendNotificationEmail(user, "Nouveau message");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};

    private User formateur() {
        return User.builder().id(5L).email("formateur@adac.fr").nom("Martin").prenom("Paul")
                .role(Role.ADMIN).build();
    }

    private static FeedbackRequest feedback(FeedbackType type, String page) {
        return new FeedbackRequest(type, "Le bouton ne répond pas", page,
                "Mozilla/5.0", "1280x720", "0.0.0+local");
    }

    @Test
    void sendFeedbackEmailGoesToTheConfiguredRecipientWithTheAuthorAsReplyTo() throws Exception {
        ReflectionTestUtils.setField(emailService, "feedbackRecipient", "dev@example.org");
        FeedbackRequest request = feedback(FeedbackType.BUG, "/formations/12");
        when(templateBuilder.buildFeedbackEmail("Paul Martin", Role.ADMIN, request)).thenReturn("<html>retour</html>");

        emailService.sendFeedbackEmail(formateur(), request, null);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getAllRecipients()).extracting(Object::toString).containsExactly("dev@example.org");
        assertThat(sent.getReplyTo()).extracting(Object::toString).containsExactly("formateur@adac.fr");
        assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@adac.fr");
        assertThat(sent.getSubject()).isEqualTo("[ADAC][Bug] Paul Martin — /formations/12");
    }

    @Test
    void sendFeedbackEmailLabelsSuggestionsInTheSubject() throws Exception {
        ReflectionTestUtils.setField(emailService, "feedbackRecipient", "dev@example.org");
        FeedbackRequest request = feedback(FeedbackType.SUGGESTION, "/");
        when(templateBuilder.buildFeedbackEmail(any(), any(), any())).thenReturn("<html></html>");

        emailService.sendFeedbackEmail(formateur(), request, null);

        assertThat(captureSentMessage().getSubject()).isEqualTo("[ADAC][Suggestion] Paul Martin — /");
    }

    @Test
    void sendFeedbackEmailStripsLineBreaksFromTheSubject() throws Exception {
        ReflectionTestUtils.setField(emailService, "feedbackRecipient", "dev@example.org");
        FeedbackRequest request = feedback(FeedbackType.BUG, "/a\r\nBcc: victim@example.org");
        when(templateBuilder.buildFeedbackEmail(any(), any(), any())).thenReturn("<html></html>");

        emailService.sendFeedbackEmail(formateur(), request, null);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(sent.getRecipients(jakarta.mail.Message.RecipientType.BCC)).isNull();
    }

    @Test
    void sendFeedbackEmailAttachesTheScreenshotWithItsCanonicalType() throws Exception {
        ReflectionTestUtils.setField(emailService, "feedbackRecipient", "dev@example.org");
        when(templateBuilder.buildFeedbackEmail(any(), any(), any())).thenReturn("<html>retour</html>");

        emailService.sendFeedbackEmail(formateur(), feedback(FeedbackType.BUG, "/"),
                new FeedbackScreenshot(PNG_BYTES, "image/png"));

        MimeMessage sent = captureSentMessage();
        MimeMultipart content = (MimeMultipart) sent.getContent();
        BodyPart attachment = findAttachment(content);
        assertThat(attachment).isNotNull();
        assertThat(attachment.getFileName()).isEqualTo("capture.png");
        assertThat(attachment.getContentType()).startsWith("image/png");
        assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(PNG_BYTES);
    }

    @Test
    void sendFeedbackEmailWithoutScreenshotHasNoAttachment() throws Exception {
        ReflectionTestUtils.setField(emailService, "feedbackRecipient", "dev@example.org");
        when(templateBuilder.buildFeedbackEmail(any(), any(), any())).thenReturn("<html>retour</html>");

        emailService.sendFeedbackEmail(formateur(), feedback(FeedbackType.BUG, "/"), null);

        MimeMessage sent = captureSentMessage();
        Object content = sent.getContent();
        if (content instanceof MimeMultipart multipart) {
            assertThat(findAttachment(multipart)).isNull();
        }
    }

    private static BodyPart findAttachment(MimeMultipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                return part;
            }
            if (part.getContent() instanceof MimeMultipart nested) {
                BodyPart found = findAttachment(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private MimeMessage captureSentMessage() throws MessagingException {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();

        message.saveChanges();
        return message;
    }
}
