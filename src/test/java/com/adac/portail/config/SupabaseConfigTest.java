package com.adac.portail.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test — no Spring context needed. Covers the URL-building logic this ticket actually
 * adds (see TICKET-007 review: this isn't a "simple bean" the ticket's own note exempts from
 * testing — a bug here 404s a real upload/download in prod).
 */
class SupabaseConfigTest {

    private final SupabaseConfig config = new SupabaseConfig(
            "https://xxxx.supabase.co", "service-key", "adac-documents");

    @Test
    void buildsAuthenticatedObjectUrl() {
        assertThat(config.buildObjectUrl("formations/1/programme.pdf"))
                .isEqualTo("https://xxxx.supabase.co/storage/v1/object/adac-documents/formations/1/programme.pdf");
    }

    @Test
    void buildsPublicUrl() {
        assertThat(config.buildPublicUrl("formations/1/programme.pdf"))
                .isEqualTo("https://xxxx.supabase.co/storage/v1/object/public/adac-documents/formations/1/programme.pdf");
    }

    @Test
    void encodesSpacesAndAccentsInFileName() {
        assertThat(config.buildObjectUrl("attestation réussite.pdf"))
                .isEqualTo("https://xxxx.supabase.co/storage/v1/object/adac-documents/attestation%20r%C3%A9ussite.pdf");
    }

    @Test
    void rejectsLeadingSlash() {
        assertThatThrownBy(() -> config.buildObjectUrl("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> config.buildObjectUrl("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> config.buildObjectUrl("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getterExposeConfiguredValuesWithoutHardcodingSecrets() {
        assertThat(config.getUrl()).isEqualTo("https://xxxx.supabase.co");
        assertThat(config.getKey()).isEqualTo("service-key");
        assertThat(config.getBucket()).isEqualTo("adac-documents");
    }
}
