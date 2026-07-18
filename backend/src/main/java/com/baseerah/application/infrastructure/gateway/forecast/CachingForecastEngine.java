package com.baseerah.application.infrastructure.gateway.forecast;

import com.baseerah.domain.forecast.ForecastResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * A memoizing decorator in front of the {@link ForecastEngine} interface (Step 7.3 performance hardening,
 * DESIGN.md §9). Projections are the single most expensive analytics computation — the bank report alone
 * projects the same client over the same 365-day horizon twice per request (once for the stamina score in
 * {@code UnderwritingService}, once for the report chart in {@code BankService}), and a warm demo re-hits
 * the same persona's score/forecast/rescue paths repeatedly. This decorator turns every projection after the
 * first for a given {@code (client, horizon, day)} into an in-memory map lookup.
 *
 * <p><strong>Why a hand-rolled decorator rather than Spring's cache abstraction.</strong> The step names
 * this exact seam — the caching seam should sit in front of the {@link ForecastEngine} interface, not
 * inside {@link HeuristicForecastEngine} — so a swapped-in Python sidecar inherits the cache for free.
 * Keeping it a plain {@code @Primary} decorator over the interface (rather than an {@code @Cacheable}
 * annotation on the concrete engine) honours that, matches the project's zero-extra-dependency ethos (no
 * {@code spring-boot-starter-cache} on the classpath), and keeps the cache key explicit — which matters
 * because correctness hinges on it (see below).
 *
 * <p><strong>Correctness / invalidation story.</strong> A projection is a pure function of the client's
 * telemetry and the day it is anchored to ({@link HeuristicForecastEngine#project} reads
 * {@code LocalDate.now(clock)} as "today"). The {@code data-mocks/} <em>feed</em> telemetry is frozen at seed
 * time and never mutated at runtime (DESIGN.md §10; rescue-confirm, loan and claim writes touch
 * {@code rescue_events}/{@code rewards_ledger}, never {@code transactions}). Including {@code today} in the key
 * keeps entries correct across a midnight rollover: at a new day the anchor changes, a fresh entry is computed,
 * and the previous day's entries are simply never read again.
 *
 * <p><strong>Phase 11 — telemetry is no longer immutable.</strong> A client's <em>declared periodic
 * expenses</em> now feed the projection ({@link HeuristicForecastEngine} adds them as scheduled outflows), and
 * they are user-authored: created, edited, and deactivated at runtime. So the "frozen telemetry → never needs
 * eviction" assumption no longer holds for the declared-expense dimension. {@link #evict(UUID)} drops every
 * cached entry for a client (all horizons, all anchor days); {@code DeclaredExpenseService} calls it on each
 * declared-expense write, so the next forecast recomputes with the new obligation instead of returning a stale
 * projection. Feed-only clients still never evict (nothing mutates their telemetry), so their cache behaviour
 * is unchanged.
 */
@Service
@Primary
public class CachingForecastEngine implements ForecastEngine {

    private final ForecastEngine delegate;
    private final Clock clock;
    private final Map<Key, ForecastResult> cache = new ConcurrentHashMap<>();

    /**
     * Wrap the real projection engine. Injecting the concrete {@link HeuristicForecastEngine} as the delegate
     * is deliberate: this decorator <em>is</em> the composition root for the forecasting strategy (the one
     * place allowed to name the implementation), so every other collaborator keeps injecting the
     * {@link ForecastEngine} interface and transparently gets the cache.
     */
    @Autowired
    public CachingForecastEngine(HeuristicForecastEngine delegate) {
        this(delegate, Clock.systemUTC());
    }

    CachingForecastEngine(ForecastEngine delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public ForecastResult project(UUID clientId, int horizonDays) {
        Key key = new Key(clientId, horizonDays, LocalDate.now(clock));
        return cache.computeIfAbsent(key, k -> delegate.project(k.clientId(), k.horizonDays()));
    }

    /**
     * Drop every cached projection for {@code clientId} — all horizons and all anchor days — so the next
     * projection is recomputed from the client's current telemetry. Called on any declared-expense write (see
     * class Javadoc); the delegate is also asked to evict, so a future caching delegate stays consistent.
     */
    @Override
    public void evict(UUID clientId) {
        cache.keySet().removeIf(key -> key.clientId().equals(clientId));
        delegate.evict(clientId);
    }

    /** Cache key: a projection is invariant for a fixed client, horizon, and anchor day (see class Javadoc). */
    private record Key(UUID clientId, int horizonDays, LocalDate day) {
    }
}
