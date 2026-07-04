package com.baseerah.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link LoanApplication}. Backs the Step 6.2 applicant-queue reads and the
 * {@link BankApplicantSeeder}'s idempotency check.
 */
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    /** Whether an applicant with this stable seed key already exists — the seeder's no-op guard. */
    boolean existsBySeedKey(String seedKey);

    /** The queue ordered oldest-first, for the Step 6.3 Applications pipeline. */
    List<LoanApplication> findAllByOrderByCreatedAtAsc();

    /** Only applicants that have been underwritten (a verdict has been computed). */
    List<LoanApplication> findByVerdictNotNull();

    /** Look up a seeded applicant by its stable seed key (used by tests and re-underwriting). */
    Optional<LoanApplication> findBySeedKey(String seedKey);
}
