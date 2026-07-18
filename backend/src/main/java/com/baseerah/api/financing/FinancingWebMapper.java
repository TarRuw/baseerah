package com.baseerah.api.financing;

import com.baseerah.api.loan.LoanSimulateResponse;
import com.baseerah.api.loan.LoanWebMapper;
import com.baseerah.application.financing.FinancingChoiceOutcome;
import com.baseerah.application.financing.FinancingRequestReport;
import com.baseerah.application.financing.ProposalWithImpact;
import com.baseerah.domain.financing.FinancingProposal;
import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.domain.financing.FinancingRequest;
import com.baseerah.shared.Messages;
import java.util.List;

/**
 * Projects the application-layer financing read models to their API views. Pure and static — the controller
 * calls it directly, threading in the {@link Messages} resolver for the request locale. Reuses
 * {@link LoanWebMapper} to render each replied proposal's affordability impact (instalment/DTI/verdict +
 * band colours + projected score), so the financing proposal card and the Simulate screen present identical
 * numbers and palette.
 */
public final class FinancingWebMapper {

    private FinancingWebMapper() {
    }

    /** Project a request report (request + proposals-with-impact) to its wire view. */
    public static FinancingRequestResponse toResponse(FinancingRequestReport report, Messages messages) {
        FinancingRequest request = report.request();
        List<FinancingProposalResponse> proposals = report.proposals().stream()
                .map(p -> toProposalResponse(p, messages))
                .toList();
        return new FinancingRequestResponse(request.id().toString(), request.amount(),
                request.status().name(), proposals);
    }

    /** Project a confirmed choice's before/after score to its wire view. */
    public static FinancingChooseResponse toChooseResponse(FinancingChoiceOutcome outcome) {
        return new FinancingChooseResponse(outcome.scoreBefore(), outcome.scoreAfter());
    }

    private static FinancingProposalResponse toProposalResponse(ProposalWithImpact withImpact,
            Messages messages) {
        FinancingProposal proposal = withImpact.proposal();
        LoanQuote impact = withImpact.impact();
        LoanSimulateResponse impactResponse =
                impact == null ? null : LoanWebMapper.toResponse(impact, messages);
        String firstPayment = proposal.firstPaymentDate() == null
                ? null : proposal.firstPaymentDate().toString();
        return new FinancingProposalResponse(proposal.id().toString(), proposal.bankName(),
                proposal.status().name(), proposal.rate(), proposal.termMonths(), proposal.amount(),
                firstPayment, impactResponse);
    }
}
