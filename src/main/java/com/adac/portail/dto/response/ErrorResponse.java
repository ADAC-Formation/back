package com.adac.portail.dto.response;

import java.util.List;

public record ErrorResponse(int status, String message, List<String> details) {

    public ErrorResponse(int status, String message) {
        this(status, message, List.of());
    }
}
