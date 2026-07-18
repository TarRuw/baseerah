package com.baseerah.application.auth;

/**
 * Thrown when OTP verification fails — a wrong code <em>or</em> an unknown mobile. The two cases are
 * deliberately indistinguishable to the client (a single generic 401) to avoid revealing which mobiles
 * are registered. Mapped to the shared error envelope by the api-layer {@code AuthExceptionHandler}.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
