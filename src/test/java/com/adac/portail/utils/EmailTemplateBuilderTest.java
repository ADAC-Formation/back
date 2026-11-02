package com.adac.portail.utils;

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
}
