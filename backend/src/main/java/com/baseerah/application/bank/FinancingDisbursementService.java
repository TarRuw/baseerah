package com.baseerah.application.bank;

import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestRepository;
import com.baseerah.application.loan.LoanService;
import com.baseerah.application.stress.StressScoreService;
import com.baseerah.domain.financing.FinancingStatus;
import com.baseerah.domain.financing.ProposalStatus;
import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.shared.ConflictException;
import com.baseerah.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank-side application service for the final stage of the financing lifecycle: funding the offers a consumer
 * accepted. Lists the disbursements queue ({@code ACCEPTED} proposals, each with a final affordability signal
 * from the loan engine), and records the operator's decision — {@code disburse} (→ facility active, first
 * repayment scheduled for next month) or {@code decline} (→ the request reopens so the consumer can accept a
 * different offer).
 *
 * <p>Mirrors {@code BankService.decide}'s load → guard → mutate → save → view shape (the JPA entity never
 * crosses the web boundary). No per-bank auth — the single portal operator funds whichever bank the consumer
 * accepted.
 */
@Service
public class FinancingDisbursementService {

    private final FinancingProposalRepository proposalRepository;
    private final FinancingRequestRepository requestRepository;
    private final LoanService loanService;
    private final StressScoreService stressScoreService;
    private final Clock clock;

    @Autowired
    public FinancingDisbursementService(FinancingProposalRepository proposalRepository,
            FinancingRequestRepository requestRepository, LoanService loanService,
            StressScoreService stressScoreService) {
        this(proposalRepository, requestRepository, loanService, stressScoreService, Clock.systemUTC());
    }

    FinancingDisbursementService(FinancingProposalRepository proposalRepository,
            FinancingRequestRepository requestRepository, LoanService loanService,
            StressScoreService stressScoreService, Clock clock) {
        this.proposalRepository = proposalRepository;
        this.requestRepository = requestRepository;
        this.loanService = loanService;
        this.stressScoreService = stressScoreService;
        this.clock = clock;
    }

    /** The accepted offers awaiting a funding decision, oldest first, each with a final affordability check. */
    @Transactional
    public List<DisbursementRow> inbox() {
        return proposalRepository.findByStatusOrderByCreatedAtAsc(ProposalStatus.ACCEPTED).stream()
                .map(this::toRow)
                .toList();
    }

    /**
     * Fund an accepted offer: mark the proposal {@code DISBURSED} with a first-payment date one month out and
     * the request {@code ACTIVE} (the facility is now live).
     */
    @Transactional
    public DisbursementRow disburse(UUID proposalId) {
        FinancingProposalJpaEntity proposal = requireAccepted(proposalId);
        LocalDate firstPayment = LocalDate.now(clock).plusMonths(1);
        proposal.disburse(Instant.now(clock), firstPayment);
        proposal.getRequest().setStatus(FinancingStatus.ACTIVE);
        requestRepository.save(proposal.getRequest());
        return toRow(proposal);
    }

    /**
     * Decline at the final stage: mark the proposal {@code DECLINED} and reopen the request so the consumer can
     * accept a different offer.
     */
    @Transactional
    public DisbursementRow decline(UUID proposalId) {
        FinancingProposalJpaEntity proposal = requireAccepted(proposalId);
        proposal.setStatus(ProposalStatus.DECLINED);
        proposal.getRequest().setStatus(FinancingStatus.OPEN);
        requestRepository.save(proposal.getRequest());
        return toRow(proposal);
    }

    private FinancingProposalJpaEntity requireAccepted(UUID proposalId) {
        FinancingProposalJpaEntity proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Financing proposal not found: " + proposalId));
        if (proposal.getStatus() != ProposalStatus.ACCEPTED) {
            throw new ConflictException("Only an accepted offer can be disbursed or declined.");
        }
        return proposal;
    }

    private DisbursementRow toRow(FinancingProposalJpaEntity proposal) {
        FinancingRequestJpaEntity request = proposal.getRequest();
        String clientId = request.getClient().getId().toString();
        LoanQuote quote = loanService.simulate(
                clientId, proposal.getAmount(), proposal.getRate(), proposal.getTermMonths());
        int clientScore = stressScoreService.latestFor(clientId).score();
        return new DisbursementRow(proposal.getId(), request.getId(), proposal.getBankName(),
                request.getClient().getProfileLabel(), proposal.getAmount(), proposal.getRate(),
                proposal.getTermMonths(), clientScore, quote.installment(), quote.verdict().name(),
                proposal.getStatus());
    }
}
