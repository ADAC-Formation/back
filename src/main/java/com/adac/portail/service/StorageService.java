package com.adac.portail.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * TICKET-026 — thin HTTP layer over Supabase Storage, on top of the URLs {@link
 * com.adac.portail.config.SupabaseConfig} builds (see its Javadoc, which already named this
 * class). {@code SupabaseConfig} itself stays pure URL/config, unit-testable without HTTP; the
 * actual request plumbing (auth headers, request/response bodies) lives here instead, per
 * docs/ARCHI.md's package layout.
 */
public interface StorageService {

    /**
     * Uploads {@code file}'s bytes to {@code path} under {@code contentType} and returns the
     * resulting authenticated object URL (see {@code SupabaseConfig.buildObjectUrl}) — the value
     * saved as {@code Document.fileUrl}. {@code contentType} is caller-supplied (server-derived,
     * see {@code FileValidator.canonicalMimeType}) rather than read off the {@link MultipartFile}
     * — branch-wide review: the client's {@code Content-Type} header is unvalidated and easy to
     * spoof or omit.
     */
    String upload(MultipartFile file, String path, String contentType);

    /** Downloads the raw bytes stored at {@code path} (see {@code Document.storagePath}). */
    byte[] download(String path);

    /**
     * Deletes the object at {@code path} — branch-wide review: {@code DocumentServiceImpl.deleteDocument}
     * previously only removed the database row, leaving the file permanently retrievable in
     * Supabase (an RGPD erasure gap for stagiaire-targeted documents). Idempotent: deleting an
     * already-gone object is not an error from the caller's perspective.
     */
    void delete(String path);
}
