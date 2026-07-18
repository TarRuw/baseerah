package com.baseerah.application.financing;

import com.baseerah.domain.financing.FinancingRequest;
import java.util.List;

/**
 * Application-layer read model for a financing request: the request itself plus each proposal paired with its
 * computed impact ({@link ProposalWithImpact}). The web mapper projects this to the API DTO; keeping the
 * impact out of the pure domain {@code FinancingRequest} preserves the layering (impact is derived, not
 * stored).
 *
 * @param request   the financing request (domain value)
 * @param proposals each proposal with its (nullable) loan-engine impact, in the request's proposal order
 */
public record FinancingRequestReport(FinancingRequest request, List<ProposalWithImpact> proposals) {
}
