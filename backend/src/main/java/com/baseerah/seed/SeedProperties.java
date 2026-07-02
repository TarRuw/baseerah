package com.baseerah.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for {@link MockDataSeeder}, bound from {@code baseerah.seed.*} (DESIGN.md §3, §4).
 *
 * @param dataMocksPath directory holding the {@code *.json} SAMA payloads, resolved relative to the
 *     working directory. The seeder also searches parent directories for this folder name, so the
 *     documented run (`cd backend && ./gradlew bootRun`) still finds the repo-root {@code data-mocks/}.
 * @param enabled master switch; set {@code false} to skip seeding entirely (used by tests).
 */
@ConfigurationProperties(prefix = "baseerah.seed")
public record SeedProperties(
        @DefaultValue("data-mocks") String dataMocksPath,
        @DefaultValue("true") boolean enabled) {
}
