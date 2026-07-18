package com.baseerah.config;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the scoring engines call "today" (see {@link AnalyticsProperties} for why it is not simply the
 * wall clock).
 *
 * <p>Deliberately <strong>not</strong> the only clock in the app, and not a {@code @Primary} one: a fixed
 * clock is right for scoring frozen telemetry and wrong for anything measuring real elapsed time. Injecting
 * it into {@code JwtService} would freeze token expiry — every token minted would carry the same {@code iat}
 * and the whole demo would start rejecting them the moment the fixed instant plus the TTL passed. Auth and
 * audit stamps therefore keep their own {@code Clock.systemUTC()} default, and only the analytics
 * collaborators ask for this bean by name.
 */
@Configuration
@EnableConfigurationProperties(AnalyticsProperties.class)
public class ClockConfig {

    /** The name analytics collaborators qualify on. */
    public static final String ANALYTICS_CLOCK = "analyticsClock";

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    /**
     * Fixed at midday UTC on the configured anchor, or the system clock when configured to follow it.
     *
     * <p>Midday rather than midnight so that a {@code LocalDate.now(clock)} is the anchor day in any
     * plausible display zone (Riyadh is UTC+3): midnight UTC would read as the previous day west of
     * Greenwich and make the engines score a day the data does not end on.
     */
    @Bean(ANALYTICS_CLOCK)
    public Clock analyticsClock(AnalyticsProperties properties) {
        if (properties.followsSystemClock()) {
            log.info("Analytics clock: system. Scores track the wall clock — correct only if the telemetry "
                    + "is live; on the frozen data-mocks the personas drift and eventually degenerate.");
            return Clock.systemUTC();
        }
        Clock fixed = Clock.fixed(
                properties.anchor().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        log.info("Analytics clock: fixed at {} (baseerah.analytics.as-of-date). The scoring engines treat "
                + "that as today, so the frozen data-mocks personas hold the zones they were tuned for. "
                + "Set BASEERAH_AS_OF_DATE=system for live telemetry.", properties.anchor());
        return fixed;
    }
}
