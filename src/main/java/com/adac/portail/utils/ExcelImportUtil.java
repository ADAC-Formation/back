package com.adac.portail.utils;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Modalite;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.InvalidFormationDataException;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExcelImportUtil {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportUtil.class);

    private static final int COL_INTITULE = 0;
    private static final int COL_DESCRIPTION = 1;
    private static final int COL_DATE_DEBUT = 2;
    private static final int COL_DATE_FIN = 3;
    private static final int COL_MODALITE = 4;
    private static final int COL_CATEGORIE = 5;
    private static final int COL_FORMATEUR = 6;

    private static final int HEADER_ROW_INDEX = 0;

    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;

    private static final int MAX_ROWS = 1000;

    private static final int MAX_ERRORS = 50;

    private static final int MAX_ECHOED_VALUE_LENGTH = 80;

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public List<CreateFormationRequest> parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidFormationDataException("Format invalide, seuls les fichiers .xlsx sont acceptés");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFormationDataException(
                    "Fichier trop volumineux (max " + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " Mo)");
        }

        ZipSecureFile.setMinInflateRatio(0.05);

        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException ex) {

            log.warn("Rejected formation import file '{}': not a readable .xlsx", filename, ex);
            throw new InvalidFormationDataException("Format invalide, seuls les fichiers .xlsx sont acceptés");
        }

        try {
            return parseWorkbook(workbook);
        } finally {
            try {
                workbook.close();
            } catch (IOException ex) {
                log.warn("Failed to close workbook for '{}'", filename, ex);
            }
        }
    }

    private List<CreateFormationRequest> parseWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        int lastRow = sheet.getLastRowNum();
        if (lastRow - HEADER_ROW_INDEX > MAX_ROWS) {
            throw new InvalidFormationDataException("Fichier trop volumineux (max " + MAX_ROWS + " lignes)");
        }

        List<String> errors = new ArrayList<>();
        List<CreateFormationRequest> requests = new ArrayList<>();
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row)) {
                continue;
            }

            int line = rowIndex + 1;
            RowResult result = parseRow(row, line);
            result.request().ifPresent(requests::add);
            addErrors(errors, result.errors());
        }

        if (!errors.isEmpty()) {
            throw new InvalidFormationDataException(String.join(" | ", errors));
        }
        return requests;
    }

    private void addErrors(List<String> target, List<String> toAdd) {
        for (String error : toAdd) {
            if (target.size() >= MAX_ERRORS) {
                if (target.size() == MAX_ERRORS) {
                    target.add("... et d'autres erreurs (limite de " + MAX_ERRORS + " atteinte)");
                }
                return;
            }
            target.add(error);
        }
    }

    private record RowResult(Optional<CreateFormationRequest> request, List<String> errors) {
    }

    private RowResult parseRow(Row row, int line) {
        List<String> errors = new ArrayList<>();
        CreateFormationRequest request = new CreateFormationRequest();

        String intitule = readString(row, COL_INTITULE);
        if (intitule.isBlank()) {
            errors.add(cellError(line, "intitule", "valeur manquante"));
        }
        request.setIntitule(intitule);

        String description = readString(row, COL_DESCRIPTION);
        request.setDescription(description.isBlank() ? null : description);

        readDate(row, COL_DATE_DEBUT, line, "dateDebut", errors).ifPresent(request::setDateDebut);
        readDate(row, COL_DATE_FIN, line, "dateFin", errors).ifPresent(request::setDateFin);

        String modaliteRaw = readString(row, COL_MODALITE);
        try {
            request.setModalite(Modalite.valueOf(modaliteRaw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            errors.add(cellError(line, "modalite", "valeur invalide \"" + truncate(modaliteRaw)
                    + "\" (attendu VISIO, PRESENTIEL ou MIXTE)"));
        }

        String categorieName = readString(row, COL_CATEGORIE);
        if (categorieName.isBlank()) {
            errors.add(cellError(line, "categorie", "valeur manquante"));
        } else {
            categoryRepository.findByNomIgnoreCase(categorieName)
                    .ifPresentOrElse(
                            category -> request.setCategoryId(category.getId()),
                            () -> errors.add(cellError(line, "categorie", "\"" + truncate(categorieName) + "\" introuvable")));
        }

        String formateurEmail = readString(row, COL_FORMATEUR);
        if (!formateurEmail.isBlank()) {
            resolveFormateur(formateurEmail, line, errors).ifPresent(request::setFormateurId);
        }

        if (!errors.isEmpty()) {
            return new RowResult(Optional.empty(), errors);
        }

        for (ConstraintViolation<CreateFormationRequest> violation : validator.validate(request)) {
            errors.add(cellError(line, violation.getPropertyPath().toString(), violation.getMessage()));
        }

        return errors.isEmpty() ? new RowResult(Optional.of(request), errors) : new RowResult(Optional.empty(), errors);
    }

    private Optional<Long> resolveFormateur(String email, int line, List<String> errors) {
        Optional<User> formateur = userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != Role.STAGIAIRE && user.isActive());
        if (formateur.isEmpty()) {
            errors.add(cellError(line, "formateur", "\"" + truncate(email) + "\" introuvable ou inactif"));
            return Optional.empty();
        }
        return Optional.of(formateur.get().getId());
    }

    private Optional<LocalDate> readDate(Row row, int col, int line, String columnName, List<String> errors) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add(cellError(line, columnName, "valeur manquante"));
            return Optional.empty();
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return Optional.of(cell.getLocalDateTimeCellValue().toLocalDate());
            }
            return Optional.of(LocalDate.parse(cell.getStringCellValue().trim()));
        } catch (DateTimeParseException | IllegalStateException ex) {
            errors.add(cellError(line, columnName, "date invalide (attendu AAAA-MM-JJ)"));
            return Optional.empty();
        }
    }

    private String readString(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : cellToString(cell).trim();
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!cellToString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String cellError(int line, String columnName, String message) {
        return "ligne " + line + ", colonne " + columnName + " : " + message;
    }

    private String truncate(String value) {
        return value.length() > MAX_ECHOED_VALUE_LENGTH
                ? value.substring(0, MAX_ECHOED_VALUE_LENGTH) + "…"
                : value;
    }
}
