package com.baseerah.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/auth/otp/request} — "send me a code" for a mobile number.
 *
 * @param mobile the login mobile in E.164-ish form ({@code +} then 8–15 digits), e.g. {@code +966501000001}
 */
public record OtpRequest(
        @NotBlank
        @Pattern(regexp = "\\+[0-9]{8,15}", message = "must be an E.164 mobile number, e.g. +966501000001")
        String mobile) {
}
