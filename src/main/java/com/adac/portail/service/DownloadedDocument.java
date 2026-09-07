package com.adac.portail.service;

/**
 * Transport carrier for {@code GET /api/documents/{id}/download} — not a DTO in docs/tech.md's
 * sense (the endpoint's response is a raw binary stream, not JSON; see {@code DocumentController}).
 */
public record DownloadedDocument(byte[] content, String fileName, String mimeType) {
}
