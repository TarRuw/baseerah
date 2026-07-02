package com.baseerah.common;

/**
 * Generic success envelope for the entire API surface (DESIGN.md §6).
 *
 * <p>Serializes as {@code {"status":"OK","data": ...}}, mirroring the mock data feed. Every
 * successful controller response should be wrapped via {@link #ok(Object)}.
 *
 * @param status always {@code "OK"} for success responses
 * @param data   the payload carried back to the client
 * @param <T>    the payload type
 */
public record ApiResponse<T>(String status, T data) {

    private static final String STATUS_OK = "OK";

    /** Wrap {@code data} in a success envelope with {@code status="OK"}. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(STATUS_OK, data);
    }
}
