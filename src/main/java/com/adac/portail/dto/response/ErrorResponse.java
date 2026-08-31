package com.adac.portail.dto.response;

import java.util.List;

/**
 * Standard error body — see docs/tech.md, "Format d'erreur standard". Used directly by the
 * security filters (they run before {@code GlobalExceptionHandler} can apply), and reused by it
 * once that lands.
 */
public record ErrorResponse(int status, String message, List<String> details) {

    public ErrorResponse(int status, String message) {
        this(status, message, List.of());
    }
}
