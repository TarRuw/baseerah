package com.baseerah.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/auth/otp/verify} — exchange a mobile + code for a JWT.
 *
 * @param mobile the login mobile in E.164-ish form ({@code +} then 8–15 digits)
 * @param otp    the numeric one-time code (4–8 digits); the demo accepts the configured mock value
 */
public record OtpVerifyRequest(
        @NotBlank
        @Pattern(regexp = "\\+[0-9]{8,15}", message = "must be an E.164 mobile number, e.g. +966501000001")
        String mobile,
        @NotBlank
        @Pattern(regexp = "[0-9]{4,8}", message = "must be a 4-8 digit numeric code")
        String otp) {
}
