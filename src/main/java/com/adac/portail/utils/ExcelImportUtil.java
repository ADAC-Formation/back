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

/**
 * Parses a {@code .xlsx} formation-import file into {@link CreateFormationRequest}s — see
 * docs/tech.md § 4, {@code POST /api/formations/import}.
 *
 * <p><b>Column schema is a placeholder</b> (see project memory
 * {@code ticket-023-excel-import-blocked}): Charlotte doesn't have the client's real file yet.
 * The column-index constants below are where to update it once she does — everything else
 * (multipart handling, all-or-nothing validation, wiring into {@code FormationServiceImpl})
 * doesn't depend on the exact column names.</p>
 */
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

    // Review (security): a formation-import spreadsheet has no business being anywhere near the
    // 20 MB spring.servlet.multipart.max-file-size cap (application.yml — sized for document
    // uploads, TICKET-026); a tighter cap here bounds how much a single request can inflate into
    // heap regardless of what POI/the OOXML zip format itself allows.
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    // Bounds worst-case query count (~4 queries/row — see ExcelImportUtil's review) and the size
    // of the persistence context a single @Transactional import can build up.
    private static final int MAX_ROWS = 1000;
    // An error list this long has stopped being useful feedback and started being a resource-
    // exhaustion vector (a file with thousands of bad rows building a huge String) — review.
    private static final int MAX_ERRORS = 50;
    // Caps how much of an untrusted cell's content is echoed back in a 400 body.
    private static final int MAX_ECHOED_VALUE_LENGTH = 80;

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    /**
     * Parses every data row (row 0 is the header, skipped) into a {@link CreateFormationRequest}.
     * All-or-nothing: collects every row's errors before throwing, rather than failing on the
     * first one — docs/tech.md AC: "erreur de colonne… message indiquant la ligne/colonne
     * problématique" reads as "tell me everything wrong at once", not "tell me about row 2 only
     * to have row 5 fail on the next attempt". Each row is also run through the same Bean
     * Validation {@code CreateFormationRequest} itself declares (review: a hand-built request used
     * to skip {@code @Size}/{@code @AssertTrue} entirely, since those only fire behind
     * {@code @Valid @RequestBody} — an inverted date range used to reach the DB's own CHECK
     * constraint and surface as a 500 instead of this method's documented 400).
     *
     * @throws InvalidFormationDataException if the file isn't a valid {@code .xlsx}, is larger
     *         than {@value #MAX_FILE_SIZE_BYTES} bytes, has more than {@value #MAX_ROWS} data rows,
     *         or any row has an invalid/unresolvable value
     */
    public List<CreateFormationRequest> parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidFormationDataException("Format invalide, seuls les fichiers .xlsx sont acceptés");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFormationDataException(
                    "Fichier trop volumineux (max " + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " Mo)");
        }

        // Guards against a zip-bomb-style OOXML payload expanding far past what the compressed
        // upload size suggests — POI's default ratio (0.01, i.e. up to ~100:1) is looser than this
        // endpoint needs for a spreadsheet of formation rows (review).
        ZipSecureFile.setMinInflateRatio(0.05);

        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException ex) {
            // Narrowed to just the open/parse step (review) — a DB failure or a bug inside the row
            // loop below must NOT be swallowed and reported as "your file isn't valid .xlsx" with
            // zero trace in the logs, the exact class of problem GlobalExceptionHandler.
            // handleDataIntegrityViolation was hardened against for the same reason (TICKET-019).
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
            // 1-based, human-facing line number (row 0 is the header, so data row 0 -> "ligne 2").
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

    /** One row's outcome: either a valid request, or the errors that reject the whole file. */
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

        // Re-checks the exact rules CreateFormationRequest declares (@Size, @AssertTrue
        // isDateRangeValid) — the ones @Valid @RequestBody would run on the JSON path but this
        // hand-built request would otherwise skip entirely (review).
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
