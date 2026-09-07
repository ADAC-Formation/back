package com.adac.portail.service;

import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.utils.EmailTemplateBuilder;
import jakarta.mail.MessagingException;
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

/** TICKET-034 — see docs/tickets/TICKET-034.md § Write tests first, Test 1-3. */
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
        // A real MimeMessage, not a mock: MimeMessageHelper calls real methods on whatever
        // createMimeMessage() returns, so a Mockito mock here would NPE/no-op unrealistically.
        // lenient(): the toggle-disabled test never reaches this call at all, by design.
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

    // --- sendActivationEmail / sendPasswordResetEmail ------------------------------------------
    // Always sent, independent of emailNotificationsEnabled — these are transactional emails
    // (docs/tickets/TICKET-034.md AC), not in-app-notification emails.

    // Branch-wide review (CRITICAL): the previous version of this test only verified send() was
    // called at all — it stayed green whether the message carried HTML or the raw template string
    // as plain text, the wrong subject, or no From header. Asserting the real MimeMessage content
    // is what actually proves the plain-text -> HTML migration did what it claims.
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
        // Swallowing (so a failed send never blocks e.g. account creation) is ActivationServiceImpl's
        // call to make per caller — see its own tests — not this service's.
        when(templateBuilder.buildActivationEmail(any(), any())).thenReturn("<html></html>");
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendActivationEmail(user, "123456"))
                .isInstanceOf(MailSendException.class);
    }

    // Branch-wide review (CRITICAL): the MimeMessageHelper wrap was the one branch in this class
    // with no test at all — and it's the branch ActivationServiceImpl's account-enumeration-oracle
    // avoidance depends on (its catch blocks only catch MailException). A malformed address is a
    // real way to reach MimeMessageHelper.setTo's checked MessagingException.
    @Test
    void sendActivationEmailWrapsAMalformedAddressAsMailPreparationException() {
        user.setEmail("not an address");
        when(templateBuilder.buildActivationEmail(any(), any())).thenReturn("<html></html>");

        assertThatThrownBy(() -> emailService.sendActivationEmail(user, "123456"))
                .isInstanceOf(MailPreparationException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- sendNotificationEmail (TICKET-034 AC: respects emailNotificationsEnabled) -------------

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

    private MimeMessage captureSentMessage() throws MessagingException {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        // JavaMail only rewrites the Content-Type header from the DataHandler set by
        // MimeMessageHelper.setText(...) on saveChanges()/actual transport — a real send() call
        // does this implicitly, but our mocked mailSender never does, so getContentType() would
        // read a stale default without this.
        message.saveChanges();
        return message;
    }
}
