package com.adac.portail.utils;

import com.adac.portail.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] DOCX_BYTES = {0x50, 0x4B, 0x03, 0x04};

    private final FileValidator validator = new FileValidator();

    @Test
    void acceptsAllowedExtensionsWithMatchingMagicBytes() {
        assertThatCode(() -> validator.validate(file("programme.pdf", PDF_BYTES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("photo.jpg", JPG_BYTES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("photo.jpeg", JPG_BYTES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("logo.png", PNG_BYTES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("cv.docx", DOCX_BYTES))).doesNotThrowAnyException();
    }

    @Test
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> validator.validate(file("malware.exe", new byte[] {0})))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format non autorisé");
    }

    @Test
    void rejectsFileWithNoExtension() {
        assertThatThrownBy(() -> validator.validate(file("noextension", PDF_BYTES)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format non autorisé");
    }

    @Test
    void rejectsAllowedExtensionWhoseContentDoesNotMatchItsMagicBytes() {
        MockMultipartFile fakepdf = file("fake.pdf", new byte[] {0x4D, 0x5A, 0x00, 0x00});

        assertThatThrownBy(() -> validator.validate(fakepdf))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format non autorisé");
    }

    @Test
    void rejectsFileOverTenMegabytes() {
        byte[] content = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF_BYTES, 0, content, 0, PDF_BYTES.length);
        MockMultipartFile file = file("big.pdf", content);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Fichier trop volumineux (max 10 Mo)");
    }

    @Test
    void acceptsFileExactlyAtTenMegabytes() {
        byte[] content = new byte[10 * 1024 * 1024];
        System.arraycopy(PDF_BYTES, 0, content, 0, PDF_BYTES.length);

        assertThatCode(() -> validator.validate(file("exact.pdf", content))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsFilenameContainingAQuoteCharacter() {
        assertThatThrownBy(() -> validator.validate(file("a\".pdf", PDF_BYTES)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nom de fichier invalide");
    }

    @Test
    void rejectsFilenameContainingAPathSeparator() {
        assertThatThrownBy(() -> validator.validate(file("../../etc/passwd.pdf", PDF_BYTES)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nom de fichier invalide");
    }

    @Test
    void rejectsExcessivelyLongFilename() {
        String longName = "a".repeat(250) + ".pdf";

        assertThatThrownBy(() -> validator.validate(file(longName, PDF_BYTES)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nom de fichier invalide");
    }

    @Test
    void canonicalMimeTypeIsDerivedFromExtensionNotFromClientHeader() {
        assertThat(validator.canonicalMimeType("programme.pdf")).isEqualTo("application/pdf");
        assertThat(validator.canonicalMimeType("photo.JPG")).isEqualTo("image/jpeg");
        assertThat(validator.canonicalMimeType("logo.png")).isEqualTo("image/png");
        assertThat(validator.canonicalMimeType("cv.docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }
}
