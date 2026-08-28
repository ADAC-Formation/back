package com.adac.portail.entity.enums;

/**
 * Values match {@code docs/DB_MODEL.mmd} / {@code docs/tech.md} — TICKET-003's own description
 * listed shorter names (MESSAGE, DOCUMENT, FORMATION); those three other docs agree with each
 * other, so the ticket text was corrected to match rather than the other way around.
 */
public enum NotificationType {
    NEW_MESSAGE,
    DOCUMENT_UPLOADED,
    FORMATION_UPDATED
}
