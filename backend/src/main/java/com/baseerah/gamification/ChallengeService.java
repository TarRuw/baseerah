package com.baseerah.gamification;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.common.NotFoundException;
import com.baseerah.gamification.dto.ChallengeDto;
import com.baseerah.transaction.Category;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gamified micro-saving (FR-09/10, DESIGN.md §5.6). Generates challenges tailored to a client's real
 * spending anomalies and tracks progress toward them, backing the Step 5.2 endpoints and the Step 5.3 Goals
 * screen.
 *
 * <p><strong>Anomaly heuristic (engineer decision; DESIGN §5.6 is deliberately open).</strong> Over the
 * client's trailing {@value #WINDOW_DAYS}-day window, DEBIT transactions are bucketed by
 * {@link Category#resolve(String) the enum-with-fallback category}. A category is a spending-cap candidate
 * only if it is <em>discretionary</em> (a documented {@link #DISCRETIONARY} policy set — essentials like
 * groceries/utilities and any unmodelled {@code OTHER} category are never capped) and forms a genuine habit
 * ({@code >= }{@value #MIN_HABIT_TXNS} transactions in the window). The client's <em>dominant</em>
 * discretionary category (most transactions) drives the goals; every target is sized from that client's own
 * telemetry — never a constant lifted from the prototype (Global Rule).
 *
 * <p><strong>Target sizing.</strong> Three archetypes are generated when a habit exists:
 * <ul>
 *   <li><b>WELCOME_MINDFUL</b> — a retrospective starter reward whose target is a fraction
 *       ({@value #WELCOME_ACHIEVED_FRACTION}) of what the client has already spent in the category, so it is
 *       complete on generation (the demoable "claim me" goal).</li>
 *   <li><b>CAP_&lt;category&gt;</b> — a forward monthly cap set {@value #CAP_REDUCTION} below the client's
 *       average monthly spend in the category; progress reflects how far this month's spend is under the
 *       cap.</li>
 *   <li><b>SAVE_MICRO</b> — a micro-saving target sized as {@value #SAVE_FRACTION} of the client's monthly
 *       discretionary burn; progress is the saving realised versus the client's own average.</li>
 * </ul>
 * The {@link #detect(List) detection} is a pure function of the transaction window (deterministic → stable
 * tests); the {@link #generateForClient(UUID) generation shell} reads the clock and upserts, and is
 * idempotent through the {@code (client_id, code)} key — re-running boot refreshes sizing/progress but never
 * disturbs a claimed challenge.
 */
@Service
public class ChallengeService {

    /** Trailing window (days) anomalies are detected over — matches the §5.1/§5.4 scoring/rescue windows. */
    static final int WINDOW_DAYS = 90;

    /** A discretionary category must appear at least this many times in the window to count as a habit. */
    static final int MIN_HABIT_TXNS = 8;

    /** WELCOME target = this fraction of the client's tracked category spend, so it is already achieved. */
    static final double WELCOME_ACHIEVED_FRACTION = 0.5;

    /** CAP target = the client's average monthly category spend reduced by this fraction. */
    static final double CAP_REDUCTION = 0.20;

    /** SAVE target = this fraction of the client's monthly discretionary burn. */
    static final double SAVE_FRACTION = 0.10;

    /** Targets are rounded to a clean SAR unit and floored at one unit so a goal is never SAR 0. */
    private static final BigDecimal ROUND_UNIT = BigDecimal.valueOf(100);

    // Reward-point sizing: proportional to the SAR effort of the goal, clamped to a sane band.
    private static final int POINTS_DIVISOR = 10;
    private static final int MIN_POINTS = 50;
    private static final int MAX_POINTS = 250;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    /** Currency label for progress text — English default and Arabic ({@code Accept-Language: ar}). */
    private static final String SAR_LABEL_EN = "SAR";
    private static final String SAR_LABEL_AR = "ريال";

    /**
     * Discretionary categories eligible for a spending cap (DESIGN §4.1 modelled set). Essentials
     * (groceries, telecom, utilities, healthcare, transportation, education, business) and income are
     * excluded, and any unmodelled category resolves to {@link Category#OTHER} — not in this set — so an
     * unrecognised feed category is treated conservatively and never capped.
     */
    private static final Set<Category> DISCRETIONARY = EnumSet.of(
            Category.RESTAURANTS_DINING,
            Category.RESTAURANTS_LUXURY,
            Category.SHOPPING,
            Category.SHOPPING_LUXURY,
            Category.TRAVEL_FLIGHTS,
            Category.TRAVEL_HOTELS,
            Category.ENTERTAINMENT_SUBSCRIPTIONS,
            Category.SOFTWARE_SUBSCRIPTIONS,
            Category.HEALTH_FITNESS);

    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final ChallengeRepository challengeRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final Clock clock;

    @Autowired
    public ChallengeService(TransactionRepository transactionRepository, ClientService clientService,
            ChallengeRepository challengeRepository,
            ChallengeProgressRepository challengeProgressRepository) {
        this(transactionRepository, clientService, challengeRepository, challengeProgressRepository,
                Clock.systemUTC());
    }

    ChallengeService(TransactionRepository transactionRepository, ClientService clientService,
            ChallengeRepository challengeRepository,
            ChallengeProgressRepository challengeProgressRepository, Clock clock) {
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
        Client client = clientService.requireClient(clientId.toString());
        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<Transaction> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);

        for (ChallengeSpec spec : detect(window)) {
            Challenge challenge = challengeRepository.findByClient_IdAndCode(clientId, spec.code())
                    .map(existing -> {
                        existing.refresh(spec.title(), spec.subtitle(), spec.icon(), spec.targetValue(),
                                spec.rewardPoints(), spec.categoryTrigger());
                        return existing;
                    })
                    .orElseGet(() -> new Challenge(client, spec.code(), spec.title(), spec.subtitle(),
                            spec.icon(), spec.targetValue(), spec.rewardPoints(), spec.categoryTrigger()));
            challenge = challengeRepository.save(challenge);

            Challenge saved = challenge;
            ChallengeProgress progress = challengeProgressRepository.findByChallenge_Id(saved.getId())
                    .map(existing -> {
                        existing.updateProgress(spec.currentValue(), spec.pct()); // keeps claim state
                        return existing;
                    })
                    .orElseGet(() -> new ChallengeProgress(saved, spec.currentValue(), spec.pct()));
            challengeProgressRepository.save(progress);
        }
    }

    // ── Read / mapping (Step 5.2 endpoints) ─────────────────────────────────────────────────────────

    /**
     * A client's generated challenges as {@link ChallengeDto}s, each joined with its current progress. Backs
     * {@code GET /api/v1/clients/{id}/challenges}. Resolves the client first (reusing the shared 404 contract
     * from {@link ClientService#requireClient}), so an unknown or malformed id yields the standard
     * {@code NOT_FOUND} envelope rather than an empty list. Entities never leave the service — the mapping
     * happens here (ORCHESTRATION Global Rules).
     *
     * @param clientId the client whose challenges to list
     * @param locale   the request locale (from {@code Accept-Language}) for the SAR label in progress text
     * @return the client's challenges as DTOs (empty when none were generated)
     */
    @Transactional(readOnly = true)
    public List<ChallengeDto> listForClient(UUID clientId, Locale locale) {
        clientService.requireClient(clientId.toString());
        return challengeRepository.findByClient_Id(clientId).stream()
                .map(challenge -> toDto(challenge, locale))
                .toList();
    }

    /**
     * One of a client's challenges as a {@link ChallengeDto}. Used by the claim endpoint to return the goal
     * in its post-claim state ({@code claimed = true}). Scoped to the client — a challenge that does not
     * exist or belongs to another client is reported as {@link NotFoundException} (never leaking a foreign
     * goal), matching {@link RewardsService#claimChallenge}.
     *
     * @param clientId    the owning client
     * @param challengeId the challenge to project
     * @param locale      the request locale for the SAR label in progress text
     * @return the challenge as a DTO
     * @throws NotFoundException if the challenge does not exist or does not belong to {@code clientId}
     */
    @Transactional(readOnly = true)
    public ChallengeDto challengeDtoFor(UUID clientId, UUID challengeId, Locale locale) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .filter(c -> c.getClient().getId().equals(clientId))
                .orElseThrow(() -> new NotFoundException("Challenge not found: " + challengeId));
        return toDto(challenge, locale);
    }

    /** Project a challenge + its (0..1) progress row into the client-facing DTO. */
    private ChallengeDto toDto(Challenge challenge, Locale locale) {
        ChallengeProgress progress = challengeProgressRepository.findByChallenge_Id(challenge.getId())
                .orElse(null);
        BigDecimal current = progress == null ? BigDecimal.ZERO : progress.getCurrentValue();
        int pct = progress == null ? 0 : progress.getPct();
        boolean claimed = progress != null && progress.isClaimed();
        boolean claimable = progress != null && progress.isClaimable();
        return new ChallengeDto(challenge.getId(), challenge.getIcon(), challenge.getTitle(),
                challenge.getSubtitle(), challenge.getRewardPoints(), pct,
                progressText(current, challenge.getTargetValue(), locale), claimable, claimed);
    }

    /**
     * Human-readable progress, e.g. {@code "541 / 2,000 SAR"} — thousands-grouped whole SAR to match the
     * DESIGN §8 {@code fmt(n)} number helper, with the currency label localised for the request:
     * {@code "SAR"} by default and {@code "ريال"} when the {@code Accept-Language} header is Arabic. Digits
     * stay Western (the Goals screen re-formats for full RTL display in Step 5.3).
     */
    static String progressText(BigDecimal current, BigDecimal target, Locale locale) {
        String label = isArabic(locale) ? SAR_LABEL_AR : SAR_LABEL_EN;
        return grouped(current) + " / " + grouped(target) + " " + label;
    }

    private static boolean isArabic(Locale locale) {
        return locale != null && "ar".equalsIgnoreCase(locale.getLanguage());
    }

    private static String grouped(BigDecimal value) {
        long whole = (value == null ? BigDecimal.ZERO : value).setScale(0, RoundingMode.HALF_UP).longValue();
        return String.format(Locale.US, "%,d", whole);
    }

    // ── Pure detection ─────────────────────────────────────────────────────────────────────────────

    /**
     * The challenges a transaction window implies — a pure, deterministic function of its input (no clock,
     * no DB), so the algorithm can be unit-tested directly against a persona's raw transactions. Returns an
     * empty list when the window shows no discretionary spending habit to build a goal on.
     *
     * @param window the client's transactions to derive goals from
     * @return the challenge specs (0..3), each carrying its computed {@code currentValue}/{@code pct}
     */
    public static List<ChallengeSpec> detect(List<Transaction> window) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        Map<Category, Map<YearMonth, BigDecimal>> monthly = new EnumMap<>(Category.class);
        Set<YearMonth> months = new TreeSet<>();

        for (Transaction tx : window) {
            if (tx.getDirection() != Direction.DEBIT || tx.getBookingDate() == null) {
                continue;
            }
            Category category = tx.resolveCategory();
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
            YearMonth month = YearMonth.from(tx.getBookingDate().atZone(UTC).toLocalDate());
            months.add(month);
            counts.merge(category, 1, Integer::sum);
            totals.merge(category, amount, BigDecimal::add);
            monthly.computeIfAbsent(category, k -> new HashMap<YearMonth, BigDecimal>())
                    .merge(month, amount, BigDecimal::add);
        }

        if (months.isEmpty()) {
            return List.of();
        }
        int monthCount = months.size();
        YearMonth recentMonth = ((TreeSet<YearMonth>) months).last();

        // The client's dominant discretionary habit: the discretionary category with the most transactions
        // that clears the habit threshold. Everything downstream keys off this — data-driven, per client.
        Category dominant = counts.entrySet().stream()
                .filter(e -> DISCRETIONARY.contains(e.getKey()) && e.getValue() >= MIN_HABIT_TXNS)
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (dominant == null) {
            return List.of(); // no discretionary habit → nothing to gamify honestly
        }

        List<ChallengeSpec> specs = new ArrayList<>();
        String trigger = dominant.name();
        String label = prettify(dominant);
        String icon = iconFor(dominant);

        BigDecimal dominantTotal = totals.getOrDefault(dominant, BigDecimal.ZERO);
        BigDecimal dominantMonthlyAvg = money(dominantTotal.divide(BigDecimal.valueOf(monthCount), 2,
                RoundingMode.HALF_UP));
        BigDecimal dominantRecent = monthlySpend(monthly, dominant, recentMonth);

        // 1) WELCOME_MINDFUL — retrospective starter reward, complete on generation (demoable claim).
        BigDecimal welcomeTarget = roundUnit(dominantTotal.multiply(
                BigDecimal.valueOf(WELCOME_ACHIEVED_FRACTION)));
        specs.add(new ChallengeSpec("WELCOME_MINDFUL", "Mindful spender",
                "You've already tracked SAR " + plain(dominantTotal) + " of " + label
                        + " spending — here's your starter reward.",
                "star", welcomeTarget, points(welcomeTarget), trigger,
                money(dominantTotal), pct(dominantTotal, welcomeTarget)));

        // 2) CAP_<category> — forward monthly cap below the client's own average.
        BigDecimal capTarget = roundUnit(dominantMonthlyAvg.multiply(
                BigDecimal.valueOf(1.0 - CAP_REDUCTION)));
        specs.add(new ChallengeSpec("CAP_" + trigger, "Cap your " + label + " spend",
                "Keep " + label + " under SAR " + plain(capTarget) + " this month (you average SAR "
                        + plain(dominantMonthlyAvg) + ").",
                icon, capTarget, points(capTarget), trigger,
                money(dominantRecent), capPct(dominantRecent, capTarget)));

        // 3) SAVE_MICRO — micro-saving target from the discretionary burn; progress = saving realised.
        BigDecimal discretionaryMonthly = discretionaryMonthlyBurn(totals, monthCount);
        BigDecimal discretionaryRecent = discretionaryMonthSpend(monthly, recentMonth);
        BigDecimal saveTarget = roundUnit(discretionaryMonthly.multiply(BigDecimal.valueOf(SAVE_FRACTION)));
        BigDecimal saved = money(discretionaryMonthly.subtract(discretionaryRecent).max(BigDecimal.ZERO));
        specs.add(new ChallengeSpec("SAVE_MICRO", "Micro-saving sprint",
                "Set aside SAR " + plain(saveTarget) + " by trimming your discretionary spending.",
                "savings", saveTarget, points(saveTarget), trigger,
                saved, pct(saved, saveTarget)));

        return specs;
    }

    // ── Detection helpers ────────────────────────────────────────────────────────────────────────

    private static BigDecimal monthlySpend(Map<Category, Map<YearMonth, BigDecimal>> monthly,
            Category category, YearMonth month) {
        return money(monthly.getOrDefault(category, Map.of()).getOrDefault(month, BigDecimal.ZERO));
    }

    /** Sum of every discretionary category's average monthly spend — the client's monthly discretionary burn. */
    private static BigDecimal discretionaryMonthlyBurn(Map<Category, BigDecimal> totals, int monthCount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Category, BigDecimal> e : totals.entrySet()) {
            if (DISCRETIONARY.contains(e.getKey())) {
                sum = sum.add(e.getValue());
            }
        }
        return money(sum.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP));
    }

    /** Total discretionary spend within a single month. */
    private static BigDecimal discretionaryMonthSpend(Map<Category, Map<YearMonth, BigDecimal>> monthly,
            YearMonth month) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Category, Map<YearMonth, BigDecimal>> e : monthly.entrySet()) {
            if (DISCRETIONARY.contains(e.getKey())) {
                sum = sum.add(e.getValue().getOrDefault(month, BigDecimal.ZERO));
            }
        }
        return money(sum);
    }

    /** Straight accumulation progress: {@code current/target}, clamped to {@code [0,100]}. */
    private static int pct(BigDecimal current, BigDecimal target) {
        if (target.signum() <= 0) {
            return current.signum() > 0 ? 100 : 0;
        }
        int raw = current.multiply(BigDecimal.valueOf(100))
                .divide(target, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, raw));
    }

    /**
     * "Staying under a cap" progress: 100 when this month's spend is at or below the cap, otherwise scaling
     * down as the overspend grows ({@code target/current}). Clamped to {@code [0,100]}.
     */
    private static int capPct(BigDecimal current, BigDecimal target) {
        if (target.signum() <= 0) {
            return 0;
        }
        if (current.signum() <= 0 || current.compareTo(target) <= 0) {
            return 100;
        }
        int raw = target.multiply(BigDecimal.valueOf(100))
                .divide(current, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, raw));
    }

    /** Reward points proportional to the SAR target, clamped to the {@code [MIN,MAX]} band. */
    private static int points(BigDecimal target) {
        int raw = target.divide(BigDecimal.valueOf(POINTS_DIVISOR), 0, RoundingMode.HALF_UP).intValue();
        return Math.max(MIN_POINTS, Math.min(MAX_POINTS, raw));
    }

    /** Round a SAR amount to the nearest {@link #ROUND_UNIT}, floored at one unit so a goal is never 0. */
    private static BigDecimal roundUnit(BigDecimal value) {
        BigDecimal units = value.divide(ROUND_UNIT, 0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
        return units.multiply(ROUND_UNIT).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** Human-readable category label, e.g. {@code RESTAURANTS_DINING → "restaurants dining"}. */
    private static String prettify(Category category) {
        return category.name().toLowerCase().replace('_', ' ');
    }

    /** A semantic icon token for the Goals screen (Step 5.3 maps it to a glyph). */
    private static String iconFor(Category category) {
        return switch (category) {
            case RESTAURANTS_DINING, RESTAURANTS_LUXURY -> "restaurant";
            case SHOPPING, SHOPPING_LUXURY -> "shopping";
            case TRAVEL_FLIGHTS -> "flight";
            case TRAVEL_HOTELS -> "hotel";
            case ENTERTAINMENT_SUBSCRIPTIONS, SOFTWARE_SUBSCRIPTIONS -> "subscription";
            case HEALTH_FITNESS -> "fitness";
            default -> "savings";
        };
    }

    /**
     * A generated challenge and its computed progress, before persistence. {@code targetValue} and
     * {@code currentValue} are SAR; {@code pct} is the completion percentage in {@code [0,100]}.
     */
    public record ChallengeSpec(String code, String title, String subtitle, String icon,
            BigDecimal targetValue, int rewardPoints, String categoryTrigger,
            BigDecimal currentValue, int pct) {
    }
}
