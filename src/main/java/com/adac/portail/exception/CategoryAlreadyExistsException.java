package com.adac.portail.exception;

/** 409 — a category with the same name (case-insensitive) already exists. See GlobalExceptionHandler. */
public class CategoryAlreadyExistsException extends RuntimeException {
    public CategoryAlreadyExistsException(String message) {
        super(message);
    }
}
