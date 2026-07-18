package com.baseerah.api.auth;

import com.baseerah.application.auth.InvalidOtpException;
import com.baseerah.shared.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps auth-specific failures to the shared error envelope, keeping the generic
 * {@code GlobalExceptionHandler} unaware of the auth feature. A failed OTP verification becomes a single
 * generic {@code 401} — the client cannot tell a wrong code from an unknown mobile.
 *
 * <p>Ordered <em>ahead of</em> {@code GlobalExceptionHandler} (which is {@code LOWEST_PRECEDENCE}): Spring
 * consults {@code @ControllerAdvice} beans in order and the first one with a matching handler wins, so the
 * generic advice's {@code @ExceptionHandler(Exception.class)} catch-all would otherwise shadow this
 * specific {@link InvalidOtpException} handler and turn a bad OTP into a {@code 500}. Without an explicit
 * order the two advices tie and the winner depends on classpath scan order — deterministic in an exploded
 * {@code bootRun} classpath but not in a packaged {@code bootJar}. This {@code @Order} pins the {@code 401}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuthExceptionHandler {

    private static final String UNAUTHORIZED = "UNAUTHORIZED";

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOtp(InvalidOtpException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(UNAUTHORIZED, ex.getMessage()));
    }
}
