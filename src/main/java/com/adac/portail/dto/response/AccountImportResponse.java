package com.adac.portail.dto.response;

import java.util.List;

// Résultat de POST /api/users/import (NOTES-09) : comptes créés et erreurs des lignes refusées.
public record AccountImportResponse(List<UserResponse> created, List<ImportErrorResponse> errors) {
}
