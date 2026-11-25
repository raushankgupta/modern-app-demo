package com.modernbank.common;

/** Business rule violation such as inactive account or insufficient funds (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
