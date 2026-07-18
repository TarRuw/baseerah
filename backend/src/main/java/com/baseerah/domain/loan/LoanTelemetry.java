package com.baseerah.domain.loan;

import java.math.BigDecimal;

/**
 * Monthly {@code income} and recurring {@code essentials} derived from a client's ledger telemetry (both in
 * SAR) — the output of {@link LoanCalculator#deriveTelemetry}. These are the real, per-client figures that
 * replace the prototype's hardcoded demo constants (Global Rule; DESIGN.md §5.3) before the affordability
 * verdict and score impact are computed.
 *
 * @param income     the client's monthly income
 * @param essentials the client's monthly recurring essentials
 */
public record LoanTelemetry(BigDecimal income, BigDecimal essentials) {
}
