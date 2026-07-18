package com.baseerah.api.financing;

import com.baseerah.domain.financing.FinancingOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/clients/{id}/financing/requests} — raise a financing/loan RFP.
 * {@code amount} is the amount to finance; {@code deficitInDays} is the lead time carried over from the Rescue
 * assessment (retained on the request for the audited rescue event on choose; {@code 0} for a direct request);
 * {@code banks} are the banks to fan out to (each must be one the client holds an account with — enforced in
 * the service). Bean-validated at the edge; business rules (bank ownership) are checked in the service.
 *
 * <p>Phase 12 (unified loan pipeline) adds two optional fields so the pipeline can be fed from more than
 * Rescue: {@code origin} distinguishes a Smart-Rescue request ({@code RESCUE}) from a direct
 * "apply for financing" one raised on the Simulate loan tab ({@code DIRECT}), and {@code purpose} is the
 * free-text reason shown to the bank operator. Both default in the service when omitted ({@code RESCUE} +
 * "Cover predicted deficit"), so the existing Rescue client keeps working unchanged.
 *
 * @param amount        SAR the financing must cover (positive)
 * @param deficitInDays days until the projected deficit (non-negative; {@code 0} for a direct request)
 * @param banks         one or more target bank names (non-empty, each non-blank)
 * @param origin        how the request was raised (RESCUE / DIRECT); defaults to RESCUE when omitted
 * @param purpose       free-text purpose shown to the bank; defaults per origin when blank
 */
public record CreateFinancingRequest(
        @Positive BigDecimal amount,
        @PositiveOrZero int deficitInDays,
        @NotEmpty List<@NotBlank String> banks,
        FinancingOrigin origin,
        String purpose) {
}
