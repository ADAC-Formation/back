package com.adac.portail.utils;

import com.adac.portail.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackScreenshotValidatorTest {

    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
    // "RIFF" + taille (4 octets) + "WEBP"
    private static final byte[] WEBP_BYTES =
            {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50};
    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    private static final long THREE_MB = 3L * 1024 * 1024;

    private final FeedbackScreenshotValidator validator = new FeedbackScreenshotValidator();

    @Test
    void returnsCanonicalTypeForPngJpegAndWebpWhateverTheDeclaredType() {
        assertThat(validator.validate(file(PNG_BYTES, "application/octet-stream"))).contains("image/png");
        assertThat(validator.validate(file(JPEG_BYTES, "image/png"))).contains("image/jpeg");
        assertThat(validator.validate(file(WEBP_BYTES, "image/webp"))).contains("image/webp");
    }

    @Test
    void rejectsPdfDisguisedAsPng() {
        assertThatThrownBy(() -> validator.validate(file(PDF_BYTES, "image/png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format d'image non autorisé (png, jpeg ou webp)");
    }

    @Test
    void rejectsRiffFileThatIsNotWebp() {
        byte[] wav = WEBP_BYTES.clone();
        wav[8] = 0x57; wav[9] = 0x41; wav[10] = 0x56; wav[11] = 0x45; // "WAVE"
        assertThatThrownBy(() -> validator.validate(file(wav, "image/webp")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsTruncatedHeader() {
        assertThatThrownBy(() -> validator.validate(file(new byte[] {(byte) 0x89, 0x50}, "image/png")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsExactlyThreeMegabytes() {
        assertThat(validator.validate(file(padded(PNG_BYTES, THREE_MB), "image/png"))).contains("image/png");
    }

    @Test
    void rejectsMoreThanThreeMegabytes() {
        assertThatThrownBy(() -> validator.validate(file(padded(PNG_BYTES, THREE_MB + 1), "image/png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Image trop lourde (3 Mo maximum)");
    }

    @Test
    void treatsMissingOrEmptyFileAsNoScreenshot() {
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate(file(new byte[0], "image/png"))).isEmpty();
    }

    private static MockMultipartFile file(byte[] content, String contentType) {
        return new MockMultipartFile("screenshot", "capture", contentType, content);
    }

    private static byte[] padded(byte[] header, long size) {
        byte[] content = Arrays.copyOf(header, (int) size);
        return content;
    }
}
