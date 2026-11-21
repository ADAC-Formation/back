package com.adac.portail.utils;

import com.adac.portail.dto.response.ImportErrorResponse;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// NOTES-09 : import de comptes ligne par ligne (colonnes nom, prenom, email, role).
@ExtendWith(MockitoExtension.class)
class AccountImportParserTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock
    private UserRepository userRepository;

    private AccountImportParser parser;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        parser = new AccountImportParser(userRepository, validator);
        lenient().when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private static MultipartFile xlsxOf(String[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Import");
            writeRow(sheet, 0, new String[] {"nom", "prenom", "email", "role"});
            for (int i = 0; i < rows.length; i++) {
                writeRow(sheet, i + 1, rows[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "comptes.xlsx", XLSX, out.toByteArray());
        }
    }

    private static void writeRow(Sheet sheet, int index, String[] values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
    }

    @Test
    void parsesFormateurAndStagiaireRowsWithTheirRole() throws IOException {
        AccountImportParser.Result result = parser.parse(xlsxOf(
                new String[] {"Durand", "Paul", "paul@adac.fr", "Formateur"},
                new String[] {"Petit", "Léa", "lea@adac.fr", " stagiaire "}));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).role()).isEqualTo(Role.ADMIN);
        assertThat(result.rows().get(0).request().getEmail()).isEqualTo("paul@adac.fr");
        assertThat(result.rows().get(1).role()).isEqualTo(Role.STAGIAIRE);
        assertThat(result.rows().get(1).request().getPrenom()).isEqualTo("Léa");
    }

    @Test
    void keepsValidRowsAndReportsEachInvalidCellWithItsLine() throws IOException {
        AccountImportParser.Result result = parser.parse(xlsxOf(
                new String[] {"Durand", "Paul", "paul@adac.fr", "Formateur"},
                new String[] {"", "Léa", "pas-un-email", "Directeur"}));

        assertThat(result.rows()).extracting(row -> row.request().getEmail()).containsExactly("paul@adac.fr");
        assertThat(result.errors()).containsExactlyInAnyOrder(
                new ImportErrorResponse(3, "nom", "valeur manquante"),
                new ImportErrorResponse(3, "email", "adresse e-mail invalide"),
                new ImportErrorResponse(3, "role", "valeur invalide \"Directeur\" (attendu Formateur ou Stagiaire)"));
    }

    @Test
    void reportsAnEmailThatAlreadyHasAnAccount() throws IOException {
        when(userRepository.findByEmailIgnoreCase("paul@adac.fr")).thenReturn(Optional.of(new User()));

        AccountImportParser.Result result = parser.parse(xlsxOf(
                new String[] {"Durand", "Paul", "paul@adac.fr", "Formateur"}));

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).containsExactly(new ImportErrorResponse(2, "email", "e-mail déjà utilisé"));
    }

    @Test
    void reportsAnEmailRepeatedInTheFileWithoutLosingTheFirstRow() throws IOException {
        AccountImportParser.Result result = parser.parse(xlsxOf(
                new String[] {"Durand", "Paul", "paul@adac.fr", "Formateur"},
                new String[] {"Durand", "Paul", "PAUL@adac.fr", "Stagiaire"}));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.errors()).containsExactly(
                new ImportErrorResponse(3, "email", "e-mail déjà présent ligne 2"));
    }

    @Test
    void skipsBlankRows() throws IOException {
        AccountImportParser.Result result = parser.parse(xlsxOf(
                new String[] {"", "", "", ""},
                new String[] {"Durand", "Paul", "paul@adac.fr", "Formateur"}));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(1);
    }

    @Test
    void rejectsAFileThatIsNotXlsx() {
        MultipartFile csv = new MockMultipartFile("file", "comptes.csv", "text/csv", "a,b".getBytes());

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format invalide, seuls les fichiers .xlsx sont acceptés");
    }

    @Test
    void rejectsAnUnreadableXlsx() {
        MultipartFile broken = new MockMultipartFile("file", "comptes.xlsx", XLSX, "pas un classeur".getBytes());

        assertThatThrownBy(() -> parser.parse(broken))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format invalide, seuls les fichiers .xlsx sont acceptés");
    }

    @Test
    void rejectsMoreThan200DataRows() throws IOException {
        String[][] rows = new String[201][];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new String[] {"Nom", "Prenom", "compte" + i + "@adac.fr", "Stagiaire"};
        }

        MultipartFile file = xlsxOf(rows);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Fichier trop volumineux (max 200 lignes)");
    }

    @Test
    void rejectsAFileWithoutAnyAccount() throws IOException {
        MultipartFile file = xlsxOf();
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le fichier ne contient aucun compte");
    }
}
