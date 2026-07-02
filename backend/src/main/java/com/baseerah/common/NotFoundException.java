package com.baseerah.common;

/**
 * Thrown by services when a requested domain resource does not exist.
 *
 * <p>{@link GlobalExceptionHandler} maps this to HTTP 404 with code {@code NOT_FOUND}. Prefer
 * throwing this over returning {@code null} so the error envelope stays consistent everywhere.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
