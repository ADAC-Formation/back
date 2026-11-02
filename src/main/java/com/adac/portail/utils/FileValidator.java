package com.adac.portail.utils;

import com.adac.portail.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class FileValidator {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

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

    public String canonicalMimeType(String filename) {
        return MIME_TYPES_BY_EXTENSION.get(extensionOf(filename));
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private boolean matchesSignature(MultipartFile file, String extension) {
        byte[] header = readHeader(file);
        return switch (extension) {
            case "pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "docx" -> startsWith(header, 0x50, 0x4B, 0x03, 0x04);
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
