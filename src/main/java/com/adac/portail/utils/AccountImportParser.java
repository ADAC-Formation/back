package com.adac.portail.utils;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.response.ImportErrorResponse;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Lit un fichier d'import de comptes (NOTES-09) : une ligne d'en-tête ignorée, puis une ligne par compte
// (A nom, B prenom, C email, D role = Formateur | Stagiaire). Chaque ligne est jugée seule : les lignes
// valides sont rendues, les autres donnent une erreur par cellule. Un fichier illisible ou trop grand est
// refusé en entier (BadRequestException).
@Component
@RequiredArgsConstructor
public class AccountImportParser {

    private static final int COL_NOM = 0;
    private static final int COL_PRENOM = 1;
    private static final int COL_EMAIL = 2;
    private static final int COL_ROLE = 3;

    private static final int HEADER_ROW_INDEX = 0;
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    // Chaque compte créé envoie un e-mail d'activation : plafond plus bas que l'import des formations.
    private static final int MAX_ROWS = 200;
    private static final int MAX_ECHOED_VALUE_LENGTH = 80;

    private static final Map<String, Role> ROLES_BY_LABEL = Map.of(
            "formateur", Role.ADMIN,
            "stagiaire", Role.STAGIAIRE);

    private final UserRepository userRepository;
    private final Validator validator;

    public record AccountRow(CreateUserRequest request, Role role) {
    }

    public record Result(List<AccountRow> rows, List<ImportErrorResponse> errors) {
    }

    public Result parse(MultipartFile file) {
        return ExcelWorkbooks.read(file, MAX_FILE_SIZE_BYTES, BadRequestException::new, this::parseWorkbook);
    }

    private Result parseWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        int lastRow = sheet.getLastRowNum();
        if (lastRow - HEADER_ROW_INDEX > MAX_ROWS) {
            throw new BadRequestException("Fichier trop volumineux (max " + MAX_ROWS + " lignes)");
        }

        List<AccountRow> rows = new ArrayList<>();
        List<ImportErrorResponse> errors = new ArrayList<>();
        Map<String, Integer> firstLineByEmail = new HashMap<>();
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || ExcelWorkbooks.isBlankRow(row)) {
                continue;
            }
            int line = rowIndex + 1;
            List<ImportErrorResponse> rowErrors = new ArrayList<>();
            parseRow(row, line, firstLineByEmail, rowErrors).ifPresent(rows::add);
            errors.addAll(rowErrors);
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BadRequestException("Le fichier ne contient aucun compte");
        }
        return new Result(rows, errors);
    }

    private Optional<AccountRow> parseRow(Row row, int line, Map<String, Integer> firstLineByEmail,
                                          List<ImportErrorResponse> errors) {
        CreateUserRequest request = new CreateUserRequest(
                required(row, COL_NOM, line, "nom", errors),
                required(row, COL_PRENOM, line, "prenom", errors),
                required(row, COL_EMAIL, line, "email", errors),
                null);
        Optional<Role> role = readRole(row, line, errors);

        for (ConstraintViolation<CreateUserRequest> violation : validator.validate(request)) {
            String column = violation.getPropertyPath().toString();
            if (!isBlankViolationAlreadyReported(request, column)) {
                errors.add(new ImportErrorResponse(line, column, messageOf(violation)));
            }
        }
        if (errors.isEmpty()) {
            checkEmailIsFree(request.getEmail(), line, firstLineByEmail, errors);
        }

        return errors.isEmpty() ? role.map(value -> new AccountRow(request, value)) : Optional.empty();
    }

    private String required(Row row, int col, int line, String column, List<ImportErrorResponse> errors) {
        String value = ExcelWorkbooks.readString(row, col);
        if (value.isBlank()) {
            errors.add(new ImportErrorResponse(line, column, "valeur manquante"));
        }
        return value;
    }

    private Optional<Role> readRole(Row row, int line, List<ImportErrorResponse> errors) {
        String label = ExcelWorkbooks.readString(row, COL_ROLE);
        if (label.isBlank()) {
            errors.add(new ImportErrorResponse(line, "role", "valeur manquante"));
            return Optional.empty();
        }
        Role role = ROLES_BY_LABEL.get(label.toLowerCase(Locale.ROOT));
        if (role == null) {
            errors.add(new ImportErrorResponse(line, "role", "valeur invalide \""
                    + ExcelWorkbooks.truncate(label, MAX_ECHOED_VALUE_LENGTH) + "\" (attendu Formateur ou Stagiaire)"));
        }
        return Optional.ofNullable(role);
    }

    // Les champs vides sont déjà signalés « valeur manquante » : on ne répète pas l'erreur @NotBlank.
    private boolean isBlankViolationAlreadyReported(CreateUserRequest request, String column) {
        return switch (column) {
            case "nom" -> request.getNom().isBlank();
            case "prenom" -> request.getPrenom().isBlank();
            case "email" -> request.getEmail().isBlank();
            default -> false;
        };
    }

    private String messageOf(ConstraintViolation<CreateUserRequest> violation) {
        if (violation.getConstraintDescriptor().getAnnotation() instanceof Email) {
            return "adresse e-mail invalide";
        }
        return "valeur trop longue (255 caractères au maximum)";
    }

    private void checkEmailIsFree(String email, int line, Map<String, Integer> firstLineByEmail,
                                  List<ImportErrorResponse> errors) {
        Integer firstLine = firstLineByEmail.putIfAbsent(email.toLowerCase(Locale.ROOT), line);
        if (firstLine != null) {
            errors.add(new ImportErrorResponse(line, "email", "e-mail déjà présent ligne " + firstLine));
        } else if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            errors.add(new ImportErrorResponse(line, "email", "e-mail déjà utilisé"));
        }
    }
}
