package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * A currency/amount pair (DESIGN.md §4.1). {@code amount} is bound to {@link BigDecimal} to preserve
 * the {@code numeric(14,2)} precision the schema requires — never {@code double}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedMoney(
        @JsonProperty("currency") String currency,
        @JsonProperty("amount") BigDecimal amount) {
}
