package com.adac.portail.utils;

import com.adac.portail.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TICKET-026 — allowed document formats (pdf, jpg/jpeg, png, docx) and max size (10 Mo).
 *
 * <p>Branch-wide review: three separate agents flagged that trusting {@code
 * MultipartFile.getContentType()} (a client-supplied header, easy to spoof or simply absent) for
 * either validation or the persisted {@code mime_type} was unsafe. This class now owns the single
 * source of truth for a document's MIME type — {@link #canonicalMimeType} derives it server-side
 * from the (now magic-byte-verified) extension, and {@code DocumentServiceImpl}/{@code
 * StorageServiceImpl} use that instead of the client's header.</p>
 */
@Component
public class FileValidator {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** Also rejects anything not on this list at the {@link #canonicalMimeType} step. */
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /**
     * Filename characters allowed after the extension check already ran — blocks a {@code "}
     * (branch-wide review: a quoted-string {@code "} in the raw multipart filename let an
     * attacker inject a second {@code filename*} parameter into the response's {@code
     * Content-Disposition} header, overriding what the browser saves the file as — see
     * {@code DocumentController.downloadDocument}) as well as path separators and control
     * characters that have no legitimate reason to appear in a display file name.
     */
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[\"\\\\/\\p{Cntrl}]");
    private static final int MAX_FILENAME_LENGTH = 200;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fichier manquant");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > MAX_FILENAME_LENGTH
                || UNSAFE_FILENAME_CHARS.matcher(filename).find()) {
            throw new BadRequestException("Nom de fichier invalide");
        }
        String extension = extensionOf(filename);
        if (!MIME_TYPES_BY_EXTENSION.containsKey(extension)) {
            throw new BadRequestException("Format non autorisé");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Fichier trop volumineux (max 10 Mo)");
        }
        if (!matchesSignature(file, extension)) {
            throw new BadRequestException("Format non autorisé");
        }
    }

    /** The server-derived MIME type for an already-{@link #validate}d file — never the client's header. */
    public String canonicalMimeType(String filename) {
        return MIME_TYPES_BY_EXTENSION.get(extensionOf(filename));
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    /**
     * Magic-byte check (branch-wide review: an extension-only allowlist accepts a malicious file
     * simply renamed to {@code .pdf}, which every enrolled stagiaire and the Super Admin would
     * then download). {@code docx} is a zip container ({@code PK\x03\x04}) — this only proves the
     * upload is *some* zip-based OOXML file, not specifically a Word document; accepted as a
     * reasonable bound for this ticket's scope, not a full content parse.
     */
    private boolean matchesSignature(MultipartFile file, String extension) {
        byte[] header = readHeader(file);
        return switch (extension) {
            case "pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D); // "%PDF-"
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "docx" -> startsWith(header, 0x50, 0x4B, 0x03, 0x04); // ZIP local file header
            default -> false;
        };
    }

    private byte[] readHeader(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return bytes.length > 8 ? java.util.Arrays.copyOf(bytes, 8) : bytes;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de lecture du fichier à valider", e);
        }
    }

    private boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
