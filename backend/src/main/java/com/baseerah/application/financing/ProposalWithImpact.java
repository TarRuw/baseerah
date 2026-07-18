package com.baseerah.application.financing;

import com.baseerah.domain.financing.FinancingProposal;
import com.baseerah.domain.loan.LoanQuote;

/**
 * A financing proposal paired with its computed <em>impact</em> — how it would affect the client's situation
 * if taken: the loan-engine {@link LoanQuote} (instalment, total, DTI, affordability verdict, and the
 * projected stress score) produced by {@code LoanService.simulate} with the bank's replied rate/term. The
 * {@code impact} is {@code null} for a proposal that has not been replied to yet (nothing to price), so the
 * consumer sees a pending row with no figures.
 *
 * @param proposal the bank proposal (domain value)
 * @param impact   the loan-engine quote for taking it, or {@code null} while pending/declined
 */
public record ProposalWithImpact(FinancingProposal proposal, LoanQuote impact) {
}
