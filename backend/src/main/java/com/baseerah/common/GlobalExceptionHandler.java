package com.baseerah.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception → error-envelope mapping for the whole API (DESIGN.md §6).
 *
 * <p>Controllers stay thin: they throw (or let validation fail) and this advice renders the
 * canonical {@link ApiErrorResponse}. Handlers are ordered specific → generic.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String NOT_FOUND = "NOT_FOUND";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** Bean-validation failures on {@code @RequestBody @Valid} arguments → 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    /** Bean-validation failures on {@code @RequestParam}/{@code @PathVariable} → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::formatViolation)
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    /** Missing domain resource → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage());
    }

    /** Anything uncaught → 500, without leaking the stack trace to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR,
                "An unexpected error occurred.");
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String formatViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

    private static ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }
}
