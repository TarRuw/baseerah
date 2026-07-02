package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The account balance snapshot attached to a transaction (DESIGN.md §4.1), e.g.
 * {@code CLOSING_AVAILABLE}. Its {@link SeedMoney#amount()} is persisted as the transaction's
 * {@code closing_balance}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedBalance(
        @JsonProperty("type") String type,
        @JsonProperty("amount") SeedMoney amount) {
}
