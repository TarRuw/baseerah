package com.baseerah.common;

/**
 * Thrown by services when a request is well-formed but conflicts with the current state of a resource — the
 * request cannot be honoured without violating an invariant, yet the resource itself exists (so it is not a
 * {@link NotFoundException}).
 *
 * <p>{@link GlobalExceptionHandler} maps this to HTTP 409 with code {@code CONFLICT}. The motivating case is
 * the Step 5.2 challenge claim (FR-10): claiming an already-claimed or not-yet-complete challenge is a state
 * conflict, not a missing resource or a malformed request, so it surfaces as a 409 rather than a 404/400 or
 * an opaque 500.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
