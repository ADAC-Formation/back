package com.adac.portail.utils;

import com.adac.portail.dto.request.FeedbackRequest;
import com.adac.portail.dto.request.FeedbackType;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateBuilderTest {

    private final EmailTemplateBuilder builder = new EmailTemplateBuilder();

    @Test
    void activationEmailContainsTheCodeAndTheFirstName() {
        String html = builder.buildActivationEmail("Jane", "123456");

        assertThat(html).contains("123456").contains("Jane");
    }

    @Test
    void activationEmailIsAdacBrandedHtml() {
        String html = builder.buildActivationEmail("Jane", "123456");

        assertThat(html).containsIgnoringCase("<html").contains("#cc3d34");
    }

    @Test
    void passwordResetEmailContainsTheCodeAndTheFirstName() {
        String html = builder.buildPasswordResetEmail("Jane", "654321");

        assertThat(html).contains("654321").contains("Jane");
    }

    @Test
    void notificationEmailContainsTheGivenContent() {
        String html = builder.buildNotificationEmail("Nouveau message de Marie");

        assertThat(html).contains("Nouveau message de Marie");
    }

    @Test
    void notificationEmailEscapesHtmlInContent() {
        String html = builder.buildNotificationEmail("<b>test</b>");

        assertThat(html).contains("&lt;b&gt;test&lt;/b&gt;").doesNotContain("<b>test</b>");
    }

    @Test
    void activationEmailEscapesHtmlInPrenom() {
        String html = builder.buildActivationEmail("<script>alert(1)</script>", "123456");

        assertThat(html).doesNotContain("<script>");
    }
    private static FeedbackRequest feedback(String message) {
        return new FeedbackRequest(FeedbackType.BUG, message, "/formations/12",
                "Mozilla/5.0 Firefox/140.0", "1280x720", "0.0.0+local");
    }

    @Test
    void feedbackEmailContainsTheMessageAuthorRoleAndContext() {
        String html = builder.buildFeedbackEmail("Jane Doe", Role.ADMIN, feedback("Le bouton ne répond pas"));

        assertThat(html)
                .contains("Le bouton ne répond pas")
                .contains("Jane Doe")
                .contains("Formateur")
                .contains("Bug")
                .contains("/formations/12")
                .contains("Mozilla/5.0 Firefox/140.0")
                .contains("1280x720")
                .contains("0.0.0+local");
    }

    @Test
    void feedbackEmailShowsTheTrainingOfficerRoleLabel() {
        String html = builder.buildFeedbackEmail("Marie Martin", Role.SUPER_ADMIN, feedback("Une suggestion utile"));

        assertThat(html).contains("Chargée de formation");
    }

    @Test
    void feedbackEmailEscapesEveryUserSuppliedField() {
        FeedbackRequest hostile = new FeedbackRequest(FeedbackType.SUGGESTION,
                "<img src=x onerror=alert(1)>", "/<script>", "<b>ua</b>", "<i>1x1</i>", "<u>v</u>");

        String html = builder.buildFeedbackEmail("<s>Jane</s>", Role.ADMIN, hostile);

        assertThat(html)
                .contains("&lt;img src=x onerror=alert(1)&gt;")
                .doesNotContain("<img")
                .doesNotContain("<script>")
                .doesNotContain("<b>ua</b>")
                .doesNotContain("<i>1x1</i>")
                .doesNotContain("<u>v</u>")
                .doesNotContain("<s>Jane</s>");
    }

    @Test
    void feedbackEmailKeepsLineBreaksOfTheMessage() {
        String html = builder.buildFeedbackEmail("Jane Doe", Role.ADMIN, feedback("Ligne 1\nLigne <2>"));

        assertThat(html).contains("Ligne 1<br>Ligne &lt;2&gt;");
    }
}
