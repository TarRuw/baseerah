package com.baseerah.api.financing;

import java.math.BigDecimal;
import java.util.List;

/**
 * API view of a consumer financing request and its per-bank proposals, served by the financing endpoints
 * inside the shared success envelope. Projected from the application {@code FinancingRequestReport} by
 * {@link FinancingWebMapper}; no JPA entity crosses the controller boundary.
 *
 * @param id        the request id (UUID as string)
 * @param amount    the shortfall SAR the financing must cover
 * @param status    OPEN / CHOSEN / CANCELLED
 * @param proposals one entry per targeted bank
 */
public record FinancingRequestResponse(
        String id,
        BigDecimal amount,
        String status,
        List<FinancingProposalResponse> proposals) {
}
