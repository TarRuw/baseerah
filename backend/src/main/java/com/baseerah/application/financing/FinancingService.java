package com.baseerah.application.financing;

import com.baseerah.application.client.ClientService;
import com.baseerah.application.infrastructure.persistence.account.AccountJpaEntity;
import com.baseerah.application.infrastructure.persistence.account.AccountRepository;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestRepository;
import com.baseerah.application.infrastructure.persistence.rescue.RescueEventJpaEntity;
import com.baseerah.application.infrastructure.persistence.rescue.RescueEventRepository;
import com.baseerah.application.loan.LoanService;
import com.baseerah.application.stress.StressScoreService;
import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.domain.financing.FinancingOrigin;
import com.baseerah.domain.financing.FinancingProposal;
import com.baseerah.domain.financing.FinancingRequest;
import com.baseerah.domain.financing.FinancingStatus;
import com.baseerah.domain.financing.ProposalStatus;
import com.baseerah.domain.rescue.RescueOptionType;
import com.baseerah.shared.BadRequestException;
import com.baseerah.shared.ConflictException;
import com.baseerah.shared.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer application service for the Smart Rescue financing request-for-proposal flow. A client facing a
 * predicted deficit raises a request and fans it out to the banks they hold accounts with; each targeted bank
 * later replies (in the Bank Portal) with its own rate/term, and the client picks one.
 *
 * <p>Three use cases:
 * <ul>
 *   <li>{@link #createRequest} — validate the targeted banks against the ones the client actually holds
 *       ({@code accounts.bank_name}), then persist the request with one {@code PENDING} proposal per bank.</li>
 *   <li>{@link #latestReport} — the client's most recent request with, for each <em>replied</em> proposal,
 *       the full impact computed by re-running the real loan engine ({@link LoanService#simulate}).</li>
 *   <li>{@link #choose} — accept a replied proposal, settle the request, and log an auditable
 *       {@code rescue_events} row with the before/after stress score.</li>
 * </ul>
 *
 * <p>There is no bank entity — a bank is only a free-text {@code accounts.bank_name}; the held-bank set is the
 * distinct set of those names over the client's accounts. Reuses the loan engine for impact (never
 * re-deriving amortisation here) and the stress-score service for the current score, keeping this service an
 * orchestration shell.
 */
@Service
public class FinancingService {

    private final ClientService clientService;
    private final AccountRepository accountRepository;
    private final FinancingRequestRepository financingRequestRepository;
    private final LoanService loanService;
    private final StressScoreService stressScoreService;
    private final RescueEventRepository rescueEventRepository;

    public FinancingService(ClientService clientService, AccountRepository accountRepository,
            FinancingRequestRepository financingRequestRepository, LoanService loanService,
            StressScoreService stressScoreService, RescueEventRepository rescueEventRepository) {
        this.clientService = clientService;
        this.accountRepository = accountRepository;
        this.financingRequestRepository = financingRequestRepository;
        this.loanService = loanService;
        this.stressScoreService = stressScoreService;
        this.rescueEventRepository = rescueEventRepository;
    }

    /** Default purpose for a Rescue-originated request (used when the caller supplies none). */
    private static final String RESCUE_PURPOSE = "Cover predicted deficit";

    /** Default purpose for a direct "apply for financing" request (used when the caller supplies none). */
    private static final String DIRECT_PURPOSE = "Personal financing";

    /**
     * Raise a financing/loan request and fan it out to the given banks. Each bank must be one the client
     * actually holds an account with (else {@link BadRequestException}); duplicates in the request are
     * collapsed. The request is persisted {@code OPEN} with one {@code PENDING} proposal per bank, each
     * offering the full {@code amount}.
     *
     * <p>Phase 12 (unified pipeline): the request carries an {@code origin} and a {@code purpose} so the bank
     * queue can be fed by more than Rescue. A {@code null} {@code origin} defaults to {@link
     * FinancingOrigin#RESCUE} (the existing Rescue client sends none); a blank {@code purpose} defaults per
     * origin — "Cover predicted deficit" for RESCUE, "Personal financing" for a DIRECT request from the
     * Simulate loan tab.
     *
     * @param clientId      the requesting client (already ownership-checked at the controller)
     * @param amount        the amount to finance (SAR)
     * @param deficitInDays lead time to the deficit, retained for the audited rescue event on choose (0 direct)
     * @param banks         the banks to send the request to (subset of the client's held banks)
     * @param origin        how the request was raised (RESCUE / DIRECT); null defaults to RESCUE
     * @param purpose       free-text purpose; null/blank defaults per origin
     * @return the newly created request with its (all-pending) proposals, no impact yet
     */
    @Transactional
    public FinancingRequestReport createRequest(String clientId, BigDecimal amount, int deficitInDays,
            List<String> banks, FinancingOrigin origin, String purpose) {
        ClientJpaEntity client = clientService.requireClient(clientId);

        Set<String> requested = new LinkedHashSet<>(banks == null ? List.of() : banks);
        if (requested.isEmpty()) {
            throw new BadRequestException("Select at least one bank to request financing from.");
        }
        Set<String> held = heldBanks(client.getId());
        for (String bank : requested) {
            if (!held.contains(bank)) {
                throw new BadRequestException("You have no account with bank: " + bank);
            }
        }

        FinancingOrigin resolvedOrigin = origin == null ? FinancingOrigin.RESCUE : origin;
        String resolvedPurpose = (purpose == null || purpose.isBlank())
                ? (resolvedOrigin == FinancingOrigin.DIRECT ? DIRECT_PURPOSE : RESCUE_PURPOSE)
                : purpose.strip();

        FinancingRequestJpaEntity request = new FinancingRequestJpaEntity(client, amount, deficitInDays);
        request.setOrigin(resolvedOrigin);
        request.setPurpose(resolvedPurpose);
        for (String bank : requested) {
            request.addProposal(new FinancingProposalJpaEntity(bank, amount));
        }
        FinancingRequestJpaEntity saved = financingRequestRepository.save(request);
        return report(FinancingPersistenceMapper.toDomain(saved));
    }

    /**
     * The client's most recent financing request, with the full loan-engine impact attached to every replied
     * proposal. This is the read the Rescue flow polls while proposals move from {@code PENDING} to
     * {@code REPLIED}.
     *
     * @param clientId the requesting client
     * @return the latest request report
     * @throws NotFoundException if the client has never raised a financing request
     */
    @Transactional(readOnly = true)
    public FinancingRequestReport latestReport(String clientId) {
        UUID resolved = clientService.requireClientId(clientId);
        FinancingRequestJpaEntity request = financingRequestRepository
                .findFirstByClient_IdOrderByCreatedAtDesc(resolved)
                .orElseThrow(() -> new NotFoundException("No financing request for client: " + clientId));
        return report(FinancingPersistenceMapper.toDomain(request));
    }

    /**
     * All of the client's financing requests, newest first, each with the full loan-engine impact attached to
     * its replied proposals. Backs the consumer requests history/list (and its polling while any proposal is
     * still pending). Returns an empty list when the client has never raised a request — a list, not a 404, so
     * the screen shows an empty state rather than an error.
     *
     * @param clientId the requesting client
     * @return the client's request reports, most recent first (possibly empty)
     */
    @Transactional(readOnly = true)
    public List<FinancingRequestReport> listReports(String clientId) {
        UUID resolved = clientService.requireClientId(clientId);
        return financingRequestRepository.findByClient_IdOrderByCreatedAtDesc(resolved).stream()
                .map(FinancingPersistenceMapper::toDomain)
                .map(this::report)
                .toList();
    }

    /**
     * Accept a replied proposal's terms: mark it {@code ACCEPTED} and the request {@code ACCEPTED} (it now
     * awaits the bank's disbursement), and append an auditable {@code rescue_events} row (option
     * {@code MURABAHA}) recording the current and projected stress score. Acceptance is a commitment to the
     * terms, not disbursement — the bank funds it in a later step.
     *
     * @param clientId   the requesting client
     * @param requestId  the request being accepted against
     * @param proposalId the proposal to accept (must be {@code REPLIED} and belong to an {@code OPEN} request)
     * @return the before/after stress score
     */
    @Transactional
    public FinancingChoiceOutcome accept(String clientId, UUID requestId, UUID proposalId) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        FinancingRequestJpaEntity request = financingRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Financing request not found: " + requestId));
        if (!request.getClient().getId().equals(client.getId())) {
            throw new NotFoundException("Financing request not found: " + requestId);
        }
        if (request.getStatus() != FinancingStatus.OPEN) {
            throw new ConflictException("This request already has an accepted offer.");
        }

        FinancingProposalJpaEntity proposal = request.getProposals().stream()
                .filter(p -> p.getId().equals(proposalId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Proposal not found: " + proposalId));
        if (proposal.getStatus() != ProposalStatus.REPLIED) {
            throw new ConflictException("Only a replied proposal can be accepted.");
        }

        proposal.accept();
        request.setStatus(FinancingStatus.ACCEPTED);
        financingRequestRepository.save(request);

        int scoreBefore = stressScoreService.latestFor(clientId).score();
        int scoreAfter = loanService
                .simulate(clientId, proposal.getAmount(), proposal.getRate(), proposal.getTermMonths())
                .projectedScore();

        rescueEventRepository.save(new RescueEventJpaEntity(client, request.getAmount(),
                request.getDeficitInDays(), RescueOptionType.MURABAHA, scoreBefore, scoreAfter));
        return new FinancingChoiceOutcome(scoreBefore, scoreAfter);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────────

    /** The distinct set of bank names the client holds accounts with (nulls ignored). */
    private Set<String> heldBanks(UUID clientId) {
        return accountRepository.findByClientId(clientId).stream()
                .map(AccountJpaEntity::getBankName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Pair each proposal with its impact: any priced proposal (replied, accepted, or disbursed — i.e. one
     * carrying a rate + term) is run through the real loan engine, so the affordability figures back both the
     * offer comparison and the active-facility repayment schedule. A pending/declined one carries no impact.
     */
    private FinancingRequestReport report(FinancingRequest request) {
        List<ProposalWithImpact> withImpact = new ArrayList<>();
        for (FinancingProposal proposal : request.proposals()) {
            LoanQuote impact = null;
            if (proposal.rate() != null && proposal.termMonths() != null) {
                impact = loanService.simulate(request.clientId().toString(),
                        proposal.amount(), proposal.rate(), proposal.termMonths());
            }
            withImpact.add(new ProposalWithImpact(proposal, impact));
        }
        return new FinancingRequestReport(request, withImpact);
    }
}
