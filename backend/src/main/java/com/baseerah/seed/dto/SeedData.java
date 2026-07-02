package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The {@code data} block of a seed payload: the persona label plus the transaction list (DESIGN.md §4.1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedData(
        @JsonProperty("type") String type,
        @JsonProperty("client_profile") String clientProfile,
        @JsonProperty("transactions") List<SeedTransaction> transactions) {
}
