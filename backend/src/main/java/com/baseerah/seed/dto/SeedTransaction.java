package com.baseerah.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single transaction entry in a seed payload (DESIGN.md §4.1). Balance is the account's closing
 * balance <em>after</em> this transaction; the seeder uses the most recent one as the account's
 * {@code latest_balance}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedTransaction(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("credit_debit_indicator") String creditDebitIndicator,
        @JsonProperty("amount") SeedMoney amount,
        @JsonProperty("transaction_information") String transactionInformation,
        @JsonProperty("booking_date_time") String bookingDateTime,
        @JsonProperty("insights") SeedInsights insights,
        @JsonProperty("balance") SeedBalance balance) {
}
