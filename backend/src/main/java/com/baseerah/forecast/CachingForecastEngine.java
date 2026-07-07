package com.baseerah.forecast;

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
 * this exact seam — "the caching seam should sit in front of the {@link ForecastEngine} interface, not
 * inside {@link HeuristicForecast}" — so a swapped-in Python sidecar inherits the cache for free. Keeping it
 * a plain {@code @Primary} decorator over the interface (rather than an {@code @Cacheable} annotation on the
 * concrete engine) honours that, matches the project's zero-extra-dependency ethos (no
 * {@code spring-boot-starter-cache} on the classpath), and keeps the cache key explicit — which matters
 * because correctness hinges on it (see below).
 *
 * <p><strong>Correctness / invalidation story.</strong> A projection is a pure function of the client's
 * seeded transactions and the day it is anchored to ({@link HeuristicForecast#project} reads
 * {@code LocalDate.now(clock)} as "today"). The {@code data-mocks/} telemetry is frozen at seed time and
 * never mutated at runtime (DESIGN.md §10 — the demo runs on immutable seeded data; rescue-confirm, loan and
 * claim writes touch {@code rescue_events}/{@code rewards_ledger}, never {@code transactions}). Therefore for
 * a fixed {@code (clientId, horizonDays, today)} the result is invariant for the life of the process, and the
 * cache never needs active eviction. Including {@code today} in the key is what keeps it correct across a
 * midnight rollover: at a new day the anchor changes, a fresh entry is computed, and the previous day's
 * entries are simply never read again (a handful of stale entries per elapsed day — negligible for a local
 * demo; if telemetry ever became mutable, the key would gain a per-client telemetry version and evict on
 * write).
 */
@Service
@Primary
public class CachingForecastEngine implements ForecastEngine {

    private final ForecastEngine delegate;
    private final Clock clock;
    private final Map<Key, ForecastResult> cache = new ConcurrentHashMap<>();

    /**
     * Wrap the real projection engine. Injecting the concrete {@link HeuristicForecast} as the delegate is
     * deliberate: this decorator <em>is</em> the composition root for the forecasting strategy (the one place
     * allowed to name the implementation), so every other collaborator keeps injecting the
     * {@link ForecastEngine} interface and transparently gets the cache.
     */
    @Autowired
    public CachingForecastEngine(HeuristicForecast delegate) {
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

    /** Cache key: a projection is invariant for a fixed client, horizon, and anchor day (see class Javadoc). */
    private record Key(UUID clientId, int horizonDays, LocalDate day) {
    }
}
