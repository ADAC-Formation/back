package com.adac.portail.utils;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Function;

// Lecture commune des imports Excel (formations, comptes) : contrôle du fichier, ouverture protégée
// contre les archives piégées, fermeture garantie, et lecture des cellules en texte.
final class ExcelWorkbooks {

    static final String NOT_XLSX_MESSAGE = "Format invalide, seuls les fichiers .xlsx sont acceptés";

    private static final Logger log = LoggerFactory.getLogger(ExcelWorkbooks.class);

    private ExcelWorkbooks() {
    }

    // Ouvre `file` (.xlsx de `maxBytes` octets au plus), applique `reader` puis ferme le classeur.
    // Un fichier refusé lève l'exception construite par `invalidFile` avec le message destiné à l'utilisateur.
    static <T> T read(MultipartFile file, long maxBytes, Function<String, ? extends RuntimeException> invalidFile,
                      Function<Workbook, T> reader) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalidFile.apply(NOT_XLSX_MESSAGE);
        }
        if (file.getSize() > maxBytes) {
            throw invalidFile.apply("Fichier trop volumineux (max " + (maxBytes / (1024 * 1024)) + " Mo)");
        }

        ZipSecureFile.setMinInflateRatio(0.05);

        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException ex) {
            log.warn("Rejected import file '{}': not a readable .xlsx", filename, ex);
            throw invalidFile.apply(NOT_XLSX_MESSAGE);
        }

        try {
            return reader.apply(workbook);
        } finally {
            try {
                workbook.close();
            } catch (IOException ex) {
                log.warn("Failed to close workbook for '{}'", filename, ex);
            }
        }
    }

    static String readString(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : cellToString(cell).trim();
    }

    static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!cellToString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) + "…" : value;
    }

    private static String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
