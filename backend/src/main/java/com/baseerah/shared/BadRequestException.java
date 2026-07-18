package com.baseerah.shared;

/**
 * Thrown by services when a request is syntactically valid (it passed bean-validation) but violates a
 * business rule that could not be expressed as a field constraint — e.g. a financing request targeting a
 * bank the client holds no account with.
 *
 * <p>{@link GlobalExceptionHandler} maps this to HTTP 400 with code {@code BAD_REQUEST}. It differs from a
 * {@link ConflictException} (409, the resource exists but its state forbids the action) and a
 * {@link NotFoundException} (404, the resource does not exist): here the <em>input</em> is unacceptable.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
