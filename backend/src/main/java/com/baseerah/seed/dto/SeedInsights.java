package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Enrichment applied to a transaction by the feed: a cleansed description and a categorisation with a
 * confidence score (DESIGN.md §4.1). {@code category} is carried verbatim and resolved at read time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedInsights(
        @JsonProperty("description_cleansed") String descriptionCleansed,
        @JsonProperty("category") String category,
        @JsonProperty("category_confidence") BigDecimal categoryConfidence) {
}
