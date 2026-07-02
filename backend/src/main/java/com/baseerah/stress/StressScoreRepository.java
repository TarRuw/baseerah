package com.baseerah.stress;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link StressScore} snapshots. Snapshots are keyed by client and day; the
 * two finders back the read path (latest snapshot for the score endpoint) and the write path (locate an
 * existing row for the daily upsert).
 */
public interface StressScoreRepository extends JpaRepository<StressScore, UUID> {

    /** Most recent snapshot for a client — what the Step 2.2 score endpoint serves. */
    Optional<StressScore> findFirstByClientIdOrderByAsOfDateDesc(UUID clientId);

    /** The snapshot for a client on a specific day, if any — the upsert lookup for that day. */
    Optional<StressScore> findByClientIdAndAsOfDate(UUID clientId, LocalDate asOfDate);
}
