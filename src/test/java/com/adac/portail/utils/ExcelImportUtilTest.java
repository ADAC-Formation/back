package com.adac.portail.utils;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.InvalidFormationDataException;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImportUtilTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    private ExcelImportUtil excelImportUtil;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        excelImportUtil = new ExcelImportUtil(categoryRepository, userRepository, validator);
    }

    private static final String[] HEADER =
            {"intitule", "description", "dateDebut", "dateFin", "modalite", "categorie", "formateur"};

    private static MultipartFile xlsxOf(String[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Formations");
            writeRow(sheet, 0, HEADER);
            for (int i = 0; i < rows.length; i++) {
                writeRow(sheet, i + 1, rows[i]);
            }
            return toMultipartFile(workbook);
        }
    }

    private static MultipartFile toMultipartFile(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return new MockMultipartFile("file", "formations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private static void writeRow(Sheet sheet, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            if (values[i] != null) {
                cell.setCellValue(values[i]);
            }
        }
    }

    private static String[] validRow() {
        return new String[] {"Formation SST", "Une description", "2026-03-10", "2026-03-12",
                "PRESENTIEL", "Formation SST", ""};
    }

    @Test
    void parseValidFileReturnsMatchingCreateFormationRequest() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));

        List<CreateFormationRequest> result = excelImportUtil.parse(xlsxOf(validRow()));

        assertThat(result).hasSize(1);
        CreateFormationRequest request = result.get(0);
        assertThat(request.getIntitule()).isEqualTo("Formation SST");
        assertThat(request.getDescription()).isEqualTo("Une description");
        assertThat(request.getDateDebut()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(request.getDateFin()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(request.getModalite().name()).isEqualTo("PRESENTIEL");
        assertThat(request.getCategoryId()).isEqualTo(1L);
        assertThat(request.getFormateurId()).isNull();
    }

    @Test
    void parseResolvesFormateurByEmail() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        User formateur = User.builder().id(7L).role(Role.ADMIN).isActive(true).email("formateur@adac.fr").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));
        when(userRepository.findByEmailIgnoreCase("formateur@adac.fr")).thenReturn(Optional.of(formateur));

        String[] row = validRow();
        row[6] = "formateur@adac.fr";

        List<CreateFormationRequest> result = excelImportUtil.parse(xlsxOf(row));

        assertThat(result.get(0).getFormateurId()).isEqualTo(7L);
    }

    @Test
    void parseNonXlsxFileThrows() {
        MultipartFile file = new MockMultipartFile("file", "formations.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> excelImportUtil.parse(file))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void parseCorruptFileNamedXlsxThrows() {
        MultipartFile file = new MockMultipartFile("file", "formations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3, 4, 5});

        assertThatThrownBy(() -> excelImportUtil.parse(file))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void parseWithBlankIntituleThrowsWithLineNumber() throws Exception {
        String[] row = validRow();
        row[0] = "";

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(row)))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("intitule");
    }

    @Test
    void parseWithUnknownCategoryThrows() throws Exception {
        when(categoryRepository.findByNomIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(validRow())))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("categorie");
    }

    @Test
    void parseWithInvalidDateThrowsWithLineNumber() throws Exception {
        String[] row = validRow();
        row[2] = "pas une date";

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(row)))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("dateDebut");
    }

    @Test
    void parseAcceptsNativeExcelDateCell() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Formations");
            writeRow(sheet, 0, HEADER);
            String[] row = validRow();
            writeRow(sheet, 1, row);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Cell dateCell = sheet.getRow(1).getCell(2);
            dateCell.setCellValue(LocalDate.of(2026, 3, 10));
            dateCell.setCellStyle(dateStyle);

            List<CreateFormationRequest> result = excelImportUtil.parse(toMultipartFile(workbook));

            assertThat(result.get(0).getDateDebut()).isEqualTo(LocalDate.of(2026, 3, 10));
        }
    }

    @Test
    void parseSkipsBlankRows() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Formations");
            writeRow(sheet, 0, HEADER);
            writeRow(sheet, 1, validRow());
            sheet.createRow(2);
            writeRow(sheet, 3, validRow());

            List<CreateFormationRequest> result = excelImportUtil.parse(toMultipartFile(workbook));

            assertThat(result).hasSize(2);
        }
    }

    @Test
    void parseWithUnknownFormateurEmailThrows() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));
        when(userRepository.findByEmailIgnoreCase("nobody@adac.fr")).thenReturn(Optional.empty());

        String[] row = validRow();
        row[6] = "nobody@adac.fr";

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(row)))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("formateur");
    }

    @Test
    void parseWithInvertedDateRangeThrows() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));

        String[] row = validRow();
        row[2] = "2026-03-12";
        row[3] = "2026-03-10";

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(row)))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2");
    }

    @Test
    void parseCollectsErrorsAcrossMultipleRows() throws Exception {
        Category category = Category.builder().id(1L).nom("Formation SST").build();
        when(categoryRepository.findByNomIgnoreCase("Formation SST")).thenReturn(Optional.of(category));

        String[] rowWithBlankIntitule = validRow();
        rowWithBlankIntitule[0] = "";
        String[] rowWithBadModalite = validRow();
        rowWithBadModalite[4] = "PAS_UNE_MODALITE";

        assertThatThrownBy(() -> excelImportUtil.parse(xlsxOf(rowWithBlankIntitule, rowWithBadModalite)))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("ligne 3");
    }

    @Test
    void parseRejectsFileLargerThanLimit() {
        MultipartFile file = new MockMultipartFile("file", "formations.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[3 * 1024 * 1024]);

        assertThatThrownBy(() -> excelImportUtil.parse(file))
                .isInstanceOf(InvalidFormationDataException.class)
                .hasMessageContaining("volumineux");
    }
}
