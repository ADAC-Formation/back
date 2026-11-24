package com.adac.portail.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StorageKeysTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CV à jour - perfil profesional.docx | CV_a_jour_-_perfil_profesional.docx",
            "Pièce d'identité - recto.pdf        | Piece_d_identite_-_recto.pdf",
            "Diplôme.PDF                         | Diplome.PDF",
            "Cœur et Æther.pdf                   | Coeur_et_AEther.pdf",
            "programme.pdf                       | programme.pdf",
            "notes   (v2).xlsx                   | notes_v2.xlsx",
            "../../etc/passwd                    | etc_passwd",
            "📄 compte rendu.pdf                 | compte_rendu.pdf",
            "sans extension                      | sans_extension",
    })
    void safeFileNameKeepsOnlyStorageSafeCharacters(String original, String expected) {
        assertThat(StorageKeys.safeFileName(original)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "😀", "😀.pdf", "...", "'''.docx"})
    void safeFileNameFallsBackWhenNothingUsableRemains(String original) {
        String safe = StorageKeys.safeFileName(original);

        assertThat(safe).startsWith("fichier");
        assertThat(safe).matches("^[A-Za-z0-9._-]+$");
    }

    @Test
    void safeFileNameKeepsTheExtensionOfAnEmptyBase() {
        assertThat(StorageKeys.safeFileName("😀.pdf")).isEqualTo("fichier.pdf");
    }

    @Test
    void safeFileNameCapsTheLengthAndKeepsTheExtension() {
        String safe = StorageKeys.safeFileName("é".repeat(300) + ".docx");

        assertThat(safe).hasSizeLessThanOrEqualTo(StorageKeys.MAX_FILE_NAME_LENGTH);
        assertThat(safe).endsWith(".docx");
        assertThat(safe).matches("^e+\\.docx$");
    }
}
