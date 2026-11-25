package com.modernbank.common;

import java.time.Instant;

/** JSON error contract: {timestamp, status, error, message}. */
public record ErrorResponse(Instant timestamp, int status, String error, String message) {
}
