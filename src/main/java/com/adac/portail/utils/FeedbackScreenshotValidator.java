package com.adac.portail.utils;

import com.adac.portail.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Vérifie la capture d'écran facultative d'un retour : 3 Mo au plus, png, jpeg ou webp d'après la
 * signature réelle du fichier (le type déclaré par le navigateur est ignoré). Distinct de
 * {@link FileValidator}, qui fixe les règles des documents (formats et taille différents).
 */
@Component
public class FeedbackScreenshotValidator {

    static final long MAX_SIZE_BYTES = 3L * 1024 * 1024;
    private static final int HEADER_LENGTH = 12;

    private static final String TOO_LARGE_MESSAGE = "Image trop lourde (3 Mo maximum)";
    private static final String BAD_FORMAT_MESSAGE = "Format d'image non autorisé (png, jpeg ou webp)";

    /**
     * @return le type MIME canonique de l'image, ou vide si aucune image n'est jointe
     * @throws BadRequestException si l'image est trop lourde ou d'un format non autorisé
     */
    public Optional<String> validate(MultipartFile screenshot) {
        if (screenshot == null || screenshot.isEmpty()) {
            return Optional.empty();
        }
        if (screenshot.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException(TOO_LARGE_MESSAGE);
        }
        return Optional.of(detectImageType(readHeader(screenshot)));
    }

    private static String detectImageType(byte[] header) {
        if (startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        throw new BadRequestException(BAD_FORMAT_MESSAGE);
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la capture impossible", e);
        }
    }

    private static boolean startsWith(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
