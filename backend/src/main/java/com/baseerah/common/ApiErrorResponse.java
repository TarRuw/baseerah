package com.baseerah.common;

/**
 * Error envelope for the entire API surface (DESIGN.md §6).
 *
 * <p>Serializes as {@code {"status":"ERROR","error":{"code":...,"message":...}}}. Built by
 * {@link GlobalExceptionHandler}; controllers should throw rather than construct this directly.
 *
 * @param status always {@code "ERROR"}
 * @param error  the {@link ApiError} describing the failure
 */
public record ApiErrorResponse(String status, ApiError error) {

    private static final String STATUS_ERROR = "ERROR";

    /** Build an error envelope for the given {@code code}/{@code message}. */
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(STATUS_ERROR, new ApiError(code, message));
    }
}
