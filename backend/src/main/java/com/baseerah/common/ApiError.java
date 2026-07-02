package com.baseerah.common;

/**
 * The error payload carried inside {@link ApiErrorResponse} (DESIGN.md §6).
 *
 * @param code    machine-readable error code (e.g. {@code VALIDATION_ERROR}, {@code NOT_FOUND},
 *                {@code INTERNAL_ERROR}) — stable for clients to branch on
 * @param message human-readable, i18n-friendly description of what went wrong
 */
public record ApiError(String code, String message) {
}
