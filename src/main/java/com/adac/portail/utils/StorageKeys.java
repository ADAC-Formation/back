package com.adac.portail.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Nom de fichier utilisable dans une clé d'objet Supabase Storage. Supabase refuse une clé qui contient un
 * accent ou d'autres caractères hors ASCII (« InvalidKey ») : on ne garde que lettres, chiffres, {@code .},
 * {@code -} et {@code _}. Seule la clé de stockage est concernée : le nom enregistré et affiché reste celui
 * d'origine. Les barres obliques sont remplacées, ce qui empêche aussi de sortir du dossier prévu.
 */
public final class StorageKeys {

    public static final int MAX_FILE_NAME_LENGTH = 120;

    private static final String FALLBACK_NAME = "fichier";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[._]+|[._]+$");
    private static final Pattern EXTENSION = Pattern.compile("[A-Za-z0-9]{1,10}");

    private StorageKeys() {
    }

    public static String safeFileName(String originalName) {
        String ascii = toAscii(originalName == null ? "" : originalName);
        int dot = ascii.lastIndexOf('.');
        String extension = dot > 0 ? ascii.substring(dot + 1) : "";
        if (!EXTENSION.matcher(extension).matches()) {
            extension = "";
            dot = ascii.length();
        }

        String suffix = extension.isEmpty() ? "" : "." + extension;
        String base = clean(ascii.substring(0, dot));
        if (base.length() > MAX_FILE_NAME_LENGTH - suffix.length()) {
            base = clean(base.substring(0, MAX_FILE_NAME_LENGTH - suffix.length()));
        }
        return (base.isEmpty() ? FALLBACK_NAME : base) + suffix;
    }

    // « é » → « e » ; les ligatures, que la décomposition Unicode ne sépare pas, sont écrites en deux lettres.
    private static String toAscii(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("")
                .replace("œ", "oe").replace("Œ", "OE")
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("ß", "ss");
    }

    private static String clean(String value) {
        return EDGE_SEPARATORS.matcher(UNSAFE.matcher(value).replaceAll("_")).replaceAll("");
    }
}
