package com.baseerah.api.genai;

import jakarta.validation.constraints.NotBlank;

/** Chat request body. {@code message} must be present and non-blank (→ Step 0.4 400 VALIDATION_ERROR). */
public record ChatRequest(@NotBlank String message) {
}
