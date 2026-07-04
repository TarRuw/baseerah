package com.baseerah.gamification;

import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds each seeded persona's gamification challenges on startup (DESIGN.md §5.6), so the Step 5.2 endpoints
 * and Step 5.3 Goals screen have real data — including, for the student persona, an already-completed,
 * unclaimed challenge so the claim flow is demoable end-to-end.
 *
 * <p><strong>Runs after {@code MockDataSeeder} without an ordering annotation.</strong> It listens for
 * {@link ApplicationReadyEvent}, which Spring Boot publishes <em>after</em> all {@code ApplicationRunner}s
 * (the {@code MockDataSeeder} among them) have completed — so the clients and their transactions are already
 * in place, and no {@code @Order} needs to be added to the prior-step seeder to establish the dependency.
 *
 * <p><strong>Idempotent.</strong> Generation upserts by {@code (client_id, code)} and refreshes progress
 * without touching claim state, so a second boot produces no duplicate rows and never un-claims a reward.
 */
@Component
public class ChallengeSeeder {

    private static final Logger log = LoggerFactory.getLogger(ChallengeSeeder.class);

    private final ClientRepository clientRepository;
    private final ChallengeService challengeService;

    public ChallengeSeeder(ClientRepository clientRepository, ChallengeService challengeService) {
        this.clientRepository = clientRepository;
        this.challengeService = challengeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedChallenges() {
        var clients = clientRepository.findAll();
        if (clients.isEmpty()) {
            log.info("No seeded clients — skipping challenge generation.");
            return;
        }
        int generated = 0;
        for (Client client : clients) {
            challengeService.generateForClient(client.getId());
            generated++;
        }
        log.info("Challenge generation complete for {} client(s).", generated);
    }
}
