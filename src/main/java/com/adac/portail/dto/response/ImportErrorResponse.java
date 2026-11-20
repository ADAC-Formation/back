package com.adac.portail.dto.response;

// Une erreur d'une ligne d'un fichier importé (NOTES-09) : numéro de ligne Excel (1 = en-tête), colonne, message.
public record ImportErrorResponse(int ligne, String colonne, String message) {
}
