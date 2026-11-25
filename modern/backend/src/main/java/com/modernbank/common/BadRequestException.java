package com.modernbank.common;

/** Validation failure (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
