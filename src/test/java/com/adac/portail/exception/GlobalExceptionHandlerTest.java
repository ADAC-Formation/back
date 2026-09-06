package com.adac.portail.exception;

import com.adac.portail.dto.response.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-019 branch-wide review: {@link GlobalExceptionHandler#handleDataIntegrityViolation}
 * used to map every {@link DataIntegrityViolationException} to 409 and never log it — hiding
 * genuine server-side constraint bugs (NOT NULL, FK, check) behind a client-facing "already
 * exists" and leaving zero trace in the logs. Only a real unique-constraint violation (SQLState
 * {@code 23505}) should still produce the 409; anything else must fall through to a logged 500.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uniqueConstraintViolationReturnsConflict() {
        SQLException sqlException = new SQLException("duplicate key value", "23505");
        ConstraintViolationException cve = new ConstraintViolationException(
                "could not execute statement", sqlException,
                ConstraintViolationException.ConstraintKind.UNIQUE, "uk_users_email");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("wrapped", cve);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Conflit : cette ressource existe déjà");
    }

    @Test
    void notNullConstraintViolationReturnsInternalServerErrorNotConflict() {
        SQLException sqlException = new SQLException("null value in column", "23502");
        ConstraintViolationException cve = new ConstraintViolationException(
                "could not execute statement", sqlException,
                ConstraintViolationException.ConstraintKind.OTHER, "users_nom_not_null");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("wrapped", cve);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo(500);
    }

    @Test
    void violationWithNoConstraintCauseReturnsInternalServerError() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("no JDBC cause at all");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Branch-wide review: Formation.version (TICKET-022) and User.version (TICKET-019) both exist
    // to make a lost-update race throw this instead of silently overwriting, but neither ticket
    // added a handler — ObjectOptimisticLockingFailureException extends ConcurrencyFailureException,
    // not DataIntegrityViolationException, so it fell through to Spring Boot's default 500 body.
    @Test
    void optimisticLockingFailureReturnsConflict() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("formations", 1L);

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
    }
}
