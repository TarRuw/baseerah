package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level envelope of a {@code data-mocks/*.json} SAMA Open-Banking payload (DESIGN.md §4.1).
 * Input-only DTO — deliberately distinct from the JPA entities, which own persistence concerns.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedEnvelope(
        @JsonProperty("status") String status,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("data") SeedData data) {
}
