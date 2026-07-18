package com.baseerah.domain.gamification;

import com.baseerah.domain.kernel.Category;
import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The pure gamified-micro-saving rule (FR-09/10, DESIGN.md §5.6): given a client's trailing transaction
 * window as {@link LedgerEntry} records, it detects the discretionary-spending anomalies worth gamifying and
 * emits the challenges (with their computed progress) to persist. Framework-free — no Spring, no JPA, no
 * clock — so it is unit-tested directly against a persona's ledger and is fully deterministic.
 *
 * <p><strong>Anomaly heuristic (engineer decision; DESIGN §5.6 is deliberately open).</strong> Over the
 * client's trailing window, DEBIT entries are bucketed by their typed {@link Category}. A category is a
 * spending-cap candidate only if it is <em>discretionary</em> (the documented {@link #DISCRETIONARY} policy
 * set — essentials like groceries/utilities and any unmodelled {@code OTHER} category are never capped) and
 * forms a genuine habit ({@code >= }{@value #MIN_HABIT_TXNS} entries in the window). The client's
 * <em>dominant</em> discretionary category (most entries) drives the goals; every target is sized from that
 * client's own telemetry — never a constant lifted from the prototype (Global Rule).
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
 * {@link #detect(List)} is a pure function of the window; the application shell reads the clock, loads the
 * window, and upserts the emitted specs.
 */
public final class ChallengeProgressRule {

    /** Trailing window (days) anomalies are detected over — matches the §5.1/§5.4 scoring/rescue windows. */
    public static final int WINDOW_DAYS = 90;

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

    private ChallengeProgressRule() {
    }

    /**
     * The challenges a transaction window implies — a pure, deterministic function of its input (no clock,
     * no DB), so the algorithm can be unit-tested directly against a persona's ledger. Returns an empty list
     * when the window shows no discretionary spending habit to build a goal on.
     *
     * @param window the client's ledger entries to derive goals from
     * @return the challenge specs (0..3), each carrying its computed {@code currentValue}/{@code pct}
     */
    public static List<ChallengeSpec> detect(List<LedgerEntry> window) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        Map<Category, Map<YearMonth, BigDecimal>> monthly = new EnumMap<>(Category.class);
        Set<YearMonth> months = new TreeSet<>();

        for (LedgerEntry entry : window) {
            if (entry.direction() != Direction.DEBIT || entry.bookingDate() == null) {
                continue;
            }
            Category category = entry.category();
            BigDecimal amount = entry.amount() == null ? BigDecimal.ZERO : entry.amount().abs();
            YearMonth month = YearMonth.from(entry.bookingDate().atZone(UTC).toLocalDate());
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
        String icon = iconFor(dominant);

        // Title/subtitle are emitted as message *keys* + pre-formatted numeric args (Step 8.1); the category
        // label and copy are resolved for the reader's locale at read time in the web mapper (COPY-01 grammar
        // fix), so nothing English is baked into the persisted goal.
        BigDecimal dominantTotal = totals.getOrDefault(dominant, BigDecimal.ZERO);
        BigDecimal dominantMonthlyAvg = money(dominantTotal.divide(BigDecimal.valueOf(monthCount), 2,
                RoundingMode.HALF_UP));
        BigDecimal dominantRecent = monthlySpend(monthly, dominant, recentMonth);

        // 1) WELCOME_MINDFUL — retrospective starter reward, complete on generation (demoable claim).
        BigDecimal welcomeTarget = roundUnit(dominantTotal.multiply(
                BigDecimal.valueOf(WELCOME_ACHIEVED_FRACTION)));
        specs.add(new ChallengeSpec("WELCOME_MINDFUL", "challenge.welcome.title", "challenge.welcome.subtitle",
                plain(dominantTotal),
                "star", welcomeTarget, points(welcomeTarget), trigger,
                money(dominantTotal), pct(dominantTotal, welcomeTarget)));

        // 2) CAP_<category> — forward monthly cap below the client's own average.
        BigDecimal capTarget = roundUnit(dominantMonthlyAvg.multiply(
                BigDecimal.valueOf(1.0 - CAP_REDUCTION)));
        specs.add(new ChallengeSpec("CAP_" + trigger, "challenge.cap.title", "challenge.cap.subtitle",
                plain(capTarget) + "|" + plain(dominantMonthlyAvg),
                icon, capTarget, points(capTarget), trigger,
                money(dominantRecent), capPct(dominantRecent, capTarget)));

        // 3) SAVE_MICRO — micro-saving target from the discretionary burn; progress = saving realised.
        BigDecimal discretionaryMonthly = discretionaryMonthlyBurn(totals, monthCount);
        BigDecimal discretionaryRecent = discretionaryMonthSpend(monthly, recentMonth);
        BigDecimal saveTarget = roundUnit(discretionaryMonthly.multiply(BigDecimal.valueOf(SAVE_FRACTION)));
        BigDecimal saved = money(discretionaryMonthly.subtract(discretionaryRecent).max(BigDecimal.ZERO));
        specs.add(new ChallengeSpec("SAVE_MICRO", "challenge.save.title", "challenge.save.subtitle",
                plain(saveTarget),
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
     * A generated challenge and its computed progress, before persistence. {@code titleKey}/{@code subtitleKey}
     * are message-bundle keys (resolved for the reader's locale at read time) and {@code textArgs} is their
     * pipe-delimited, pre-formatted numeric arguments (Step 8.1, I18N-01). {@code targetValue} and
     * {@code currentValue} are SAR; {@code pct} is the completion percentage in {@code [0,100]}.
     */
    public record ChallengeSpec(String code, String titleKey, String subtitleKey, String textArgs, String icon,
            BigDecimal targetValue, int rewardPoints, String categoryTrigger,
            BigDecimal currentValue, int pct) {
    }
}
