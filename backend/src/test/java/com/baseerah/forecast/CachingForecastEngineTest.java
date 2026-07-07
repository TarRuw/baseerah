package com.baseerah.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.forecast.ForecastEngine.ForecastResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link CachingForecastEngine} — no Spring, no database. Drives the decorator with a
 * counting fake delegate and a fixed clock to prove the memoization contract that the Step 7.3 bank-report
 * double-projection fix relies on: identical {@code (client, horizon, day)} projects once; a different client
 * or horizon is a distinct key; and the cache never alters the delegate's result.
 */
class CachingForecastEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

    /** A delegate that records how many real projections it was asked to compute. */
    private static final class CountingEngine implements ForecastEngine {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ForecastResult project(UUID clientId, int horizonDays) {
            calls.incrementAndGet();
            // A result unique to the inputs so we can prove the cache returns the right entry, not just "a" entry.
            return new ForecastResult(List.of(), null, BigDecimal.valueOf(horizonDays));
        }
    }

    @Test
    void repeatedSameKeyProjectsOnceAndReturnsSameResult() {
        CountingEngine delegate = new CountingEngine();
        CachingForecastEngine caching = new CachingForecastEngine(delegate, FIXED_CLOCK);
        UUID client = UUID.randomUUID();

        ForecastResult first = caching.project(client, 365);
        ForecastResult second = caching.project(client, 365);

        assertThat(delegate.calls.get()).as("second identical projection is a cache hit").isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void distinctHorizonOrClientAreSeparateKeys() {
        CountingEngine delegate = new CountingEngine();
        CachingForecastEngine caching = new CachingForecastEngine(delegate, FIXED_CLOCK);
        UUID clientA = UUID.randomUUID();
        UUID clientB = UUID.randomUUID();

        caching.project(clientA, 30);
        caching.project(clientA, 365); // different horizon → miss
        caching.project(clientB, 30);  // different client → miss
        caching.project(clientA, 30);  // repeat of the first → hit

        assertThat(delegate.calls.get()).isEqualTo(3);
        // The 365-day entry carries its own horizon, proving keys don't collide.
        assertThat(caching.project(clientA, 365).minProjectedBalance()).isEqualByComparingTo("365");
    }
}
