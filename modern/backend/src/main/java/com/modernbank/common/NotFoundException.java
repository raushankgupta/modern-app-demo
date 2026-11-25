package com.modernbank.common;

/** Requested resource does not exist (HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
