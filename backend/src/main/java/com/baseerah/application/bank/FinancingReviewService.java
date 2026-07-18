package com.baseerah.application.bank;

import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.application.stress.StressScoreService;
import com.baseerah.domain.financing.ProposalStatus;
import com.baseerah.shared.ConflictException;
import com.baseerah.shared.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank-side application service for the financing RFP flow: the single global bank operator's console over the
 * consumer proposals. Lists the pending inbox (with a per-applicant risk hint), and records the operator's
 * reply — the profit rate + term the bank offers — or a decline.
 *
 * <p>Mirrors {@code BankService.decide}'s write shape (load → guard state → mutate → save → project to a
 * domain/read model; the JPA entity never crosses the web boundary). There is no per-bank auth: the portal is
 * one operator (DESIGN §12), so the operator replies on behalf of whichever {@code bank_name} a proposal was
 * addressed to. The risk hint reuses the consumer stress score rather than re-underwriting, keeping the read
 * cheap.
 */
@Service
public class FinancingReviewService {

    private final FinancingProposalRepository proposalRepository;
    private final StressScoreService stressScoreService;
    private final Clock clock;

    @Autowired
    public FinancingReviewService(FinancingProposalRepository proposalRepository,
            StressScoreService stressScoreService) {
        this(proposalRepository, stressScoreService, Clock.systemUTC());
    }

    FinancingReviewService(FinancingProposalRepository proposalRepository,
            StressScoreService stressScoreService, Clock clock) {
        this.proposalRepository = proposalRepository;
        this.stressScoreService = stressScoreService;
        this.clock = clock;
    }

    /** The pending financing proposals awaiting a reply, oldest first, each with applicant context. */
    @Transactional
    public List<FinancingInboxRow> inbox() {
        return proposalRepository.findByStatusOrderByCreatedAtAsc(ProposalStatus.PENDING).stream()
                .map(this::toRow)
                .toList();
    }

    /**
     * Record the operator's reply for a proposal: stamp the rate/term and flip it to {@code REPLIED}.
     *
     * @param proposalId the proposal to reply to (must be {@code PENDING})
     * @param rate       the bank's nominal annual profit rate as a percentage
     * @param termMonths the bank's repayment term in months
     * @return the updated inbox row
     */
    @Transactional
    public FinancingInboxRow reply(UUID proposalId, BigDecimal rate, int termMonths) {
        FinancingProposalJpaEntity proposal = requirePending(proposalId);
        proposal.applyReply(rate, termMonths, Instant.now(clock));
        proposalRepository.save(proposal);
        return toRow(proposal);
    }

    /**
     * Decline a proposal: flip it to {@code DECLINED} without a rate.
     *
     * @param proposalId the proposal to decline (must be {@code PENDING})
     * @return the updated inbox row
     */
    @Transactional
    public FinancingInboxRow decline(UUID proposalId) {
        FinancingProposalJpaEntity proposal = requirePending(proposalId);
        proposal.setStatus(ProposalStatus.DECLINED);
        proposalRepository.save(proposal);
        return toRow(proposal);
    }

    private FinancingProposalJpaEntity requirePending(UUID proposalId) {
        FinancingProposalJpaEntity proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Financing proposal not found: " + proposalId));
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ConflictException("Proposal has already been answered.");
        }
        return proposal;
    }

    private FinancingInboxRow toRow(FinancingProposalJpaEntity proposal) {
        FinancingRequestJpaEntity request = proposal.getRequest();
        UUID clientId = request.getClient().getId();
        int clientScore = stressScoreService.latestFor(clientId.toString()).score();
        // Phase 12: surface the parent request's underwriting outcome to the price stage (null until underwritten).
        return new FinancingInboxRow(proposal.getId(), request.getId(), proposal.getBankName(),
                request.getClient().getProfileLabel(), proposal.getAmount(), clientScore,
                request.getVerdict(), request.getStaminaScore(),
                proposal.getStatus(), proposal.getRate(), proposal.getTermMonths(), request.getCreatedAt());
    }
}
