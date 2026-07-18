package com.baseerah.application.gamification;

import com.baseerah.application.infrastructure.persistence.gamification.ChallengeJpaEntity;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengePersistenceMapper;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeProgressJpaEntity;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeProgressRepository;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeRepository;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.gamification.ChallengeProgressRule;
import com.baseerah.domain.gamification.ChallengeProgressRule.ChallengeSpec;
import com.baseerah.domain.gamification.ChallengeView;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.shared.NotFoundException;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gamified micro-saving application service (FR-09/10, DESIGN.md §5.6) — the imperative shell over the pure
 * {@link ChallengeProgressRule}. Generates challenges tailored to a client's real spending anomalies and
 * tracks progress toward them, backing the Step 5.2 endpoints and the Step 5.3 Goals screen.
 *
 * <p>The shell loads the client's transaction window, maps it to the domain {@link LedgerEntry} kernel via
 * {@link TransactionPersistenceMapper#toLedgerEntries}, and hands it to the framework-free
 * {@link ChallengeProgressRule#detect(List)}; it then upserts each emitted {@link ChallengeSpec} and returns
 * the domain {@link ChallengeView} projection (never a JPA entity). Presentation copy is resolved downstream
 * by the web mapper, so this service is locale-agnostic — it never touches {@code Messages}.
 *
 * <p>Generation is idempotent through the {@code (client_id, code)} key — a re-run refreshes sizing/progress
 * in place and leaves any claimed challenge's claim state untouched. The injectable {@link Clock} keeps the
 * detection window deterministic under test.
 */
@Service
public class ChallengeService {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final ChallengeRepository challengeRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final Clock clock;

    /**
     * The fixed-clock seam the {@code api}/{@code application} gamification tests use to anchor the window
     * inside the mock-data range — and, since the analytics clock was introduced, the one Spring wires too:
     * detection reads a trailing window over frozen telemetry, so it must anchor where the data is.
     *
     * @param clock the analytics clock — the day the frozen telemetry is scored against, not the wall clock
     *              (see {@code AnalyticsProperties}). Qualified by name, not by {@code ClockConfig}'s
     *              constant: the application layer must not import the composition root.
     */
    @Autowired
    public ChallengeService(TransactionRepository transactionRepository, ClientService clientService,
            ChallengeRepository challengeRepository,
            ChallengeProgressRepository challengeProgressRepository,
            @Qualifier("analyticsClock") Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
        this.challengeRepository = challengeRepository;
        this.challengeProgressRepository = challengeProgressRepository;
        this.clock = clock;
    }

    /**
     * Generate (or idempotently refresh) a client's challenges from their trailing transaction window and
     * persist each with its progress row. Resolves the client (404 contract reused from
     * {@link ClientService#requireClient}), detects the goals, and upserts them by {@code (client_id, code)}
     * — a re-run updates sizing/progress in place and leaves any claimed challenge's claim state untouched.
     *
     * @param clientId the client to generate challenges for
     */
    @Transactional
    public void generateForClient(UUID clientId) {
        ClientJpaEntity client = clientService.requireClient(clientId.toString());
        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(ChallengeProgressRule.WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<TransactionJpaEntity> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
        List<LedgerEntry> ledger = TransactionPersistenceMapper.toLedgerEntries(window);

        for (ChallengeSpec spec : ChallengeProgressRule.detect(ledger)) {
            ChallengeJpaEntity challenge = challengeRepository.findByClient_IdAndCode(clientId, spec.code())
                    .map(existing -> {
                        existing.refresh(spec.titleKey(), spec.subtitleKey(), spec.icon(), spec.targetValue(),
                                spec.rewardPoints(), spec.categoryTrigger(), spec.textArgs());
                        return existing;
                    })
                    .orElseGet(() -> new ChallengeJpaEntity(client, spec.code(), spec.titleKey(),
                            spec.subtitleKey(), spec.icon(), spec.targetValue(), spec.rewardPoints(),
                            spec.categoryTrigger(), spec.textArgs()));
            challenge = challengeRepository.save(challenge);

            ChallengeJpaEntity saved = challenge;
            ChallengeProgressJpaEntity progress = challengeProgressRepository.findByChallenge_Id(saved.getId())
                    .map(existing -> {
                        existing.updateProgress(spec.currentValue(), spec.pct()); // keeps claim state
                        return existing;
                    })
                    .orElseGet(() -> new ChallengeProgressJpaEntity(saved, spec.currentValue(), spec.pct()));
            challengeProgressRepository.save(progress);
        }
    }

    // ── Read / mapping (Step 5.2 endpoints) ─────────────────────────────────────────────────────────

    /**
     * A client's generated challenges as domain {@link ChallengeView}s, each joined with its current
     * progress. Backs {@code GET /api/v1/clients/{id}/challenges}. Resolves the client first (reusing the
     * shared 404 contract from {@link ClientService#requireClient}), so an unknown or malformed id yields the
     * standard {@code NOT_FOUND} envelope rather than an empty list. JPA entities never leave the service —
     * the mapping to the domain view happens here (ORCHESTRATION Global Rules).
     *
     * @param clientId the client whose challenges to list
     * @return the client's challenges as domain views (empty when none were generated)
     */
    @Transactional(readOnly = true)
    public List<ChallengeView> listForClient(UUID clientId) {
        clientService.requireClient(clientId.toString());
        List<ChallengeJpaEntity> challenges = challengeRepository.findByClient_Id(clientId);
        // Batch-load every challenge's (0..1) progress in one query, then map in-memory — was an N+1
        // (one progress lookup per challenge) before Step 7.3. There is exactly one progress row per
        // challenge (DB-enforced), so keys never collide. getChallenge().getId() reads the id off the lazy
        // proxy without a per-row query.
        Map<UUID, ChallengeProgressJpaEntity> progressByChallenge = challengeProgressRepository
                .findByChallenge_IdIn(challenges.stream().map(ChallengeJpaEntity::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getChallenge().getId(), p -> p));
        return challenges.stream()
                .map(challenge -> ChallengePersistenceMapper.toView(challenge,
                        progressByChallenge.get(challenge.getId())))
                .toList();
    }

    /**
     * One of a client's challenges as a domain {@link ChallengeView}. Used by the claim endpoint to return
     * the goal in its post-claim state ({@code claimed = true}). Scoped to the client — a challenge that does
     * not exist or belongs to another client is reported as {@link NotFoundException} (never leaking a
     * foreign goal), matching {@link RewardsService#claimChallenge}.
     *
     * @param clientId    the owning client
     * @param challengeId the challenge to project
     * @return the challenge as a domain view
     * @throws NotFoundException if the challenge does not exist or does not belong to {@code clientId}
     */
    @Transactional(readOnly = true)
    public ChallengeView challengeViewFor(UUID clientId, UUID challengeId) {
        ChallengeJpaEntity challenge = challengeRepository.findById(challengeId)
                .filter(c -> c.getClient().getId().equals(clientId))
                .orElseThrow(() -> new NotFoundException("Challenge not found: " + challengeId));
        ChallengeProgressJpaEntity progress = challengeProgressRepository
                .findByChallenge_Id(challenge.getId()).orElse(null);
        return ChallengePersistenceMapper.toView(challenge, progress);
    }
}
