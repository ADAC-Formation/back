package com.adac.portail.exception;

public class DocumentTypeAlreadyExistsException extends RuntimeException {
    public DocumentTypeAlreadyExistsException(String message) {
        super(message);
    }
}
