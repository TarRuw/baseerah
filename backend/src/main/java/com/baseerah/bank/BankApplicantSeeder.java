package com.baseerah.bank;

import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds the Bank Portal's loan-applicant queue on startup (FR-08, DESIGN.md §5.5, §11), so the Step 6.2
 * endpoints and Step 6.3 Applications pipeline have real data. Some applicants are <strong>linked</strong> to
 * seeded personas via {@code client_ref} — including {@code client_005_vip}, the §11 bank-portal demo
 * persona — and are underwritten at seed time so their row carries a verdict at rest; others are synthetic
 * queue-fillers with no telemetry, left un-underwritten (verdict {@code null}) until a banker requests a
 * report.
 *
 * <p>The linked applicants are sized to span all three §5.5 verdict bands: a small ask against a healthy
 * persona underwrites <b>OK</b>; a deliberately oversized ask forces DTI past the 71% floor for a
 * guaranteed <b>BAD</b>; a mid-sized ask lands a healthy persona in the <b>WARN</b> band. (Amounts were
 * tuned against the seeded telemetry — see the step handoff.)
 *
 * <p><strong>Runs after {@code MockDataSeeder}</strong> — like {@code ChallengeSeeder}, it listens for
 * {@link ApplicationReadyEvent}, published after all {@code ApplicationRunner}s (the seeder among them)
 * complete, so the personas and their transactions are already in place. <strong>Idempotent</strong> via
 * each applicant's stable {@code seedKey}: an existing key is skipped, so reboots never duplicate the queue.
 */
@Component
public class BankApplicantSeeder {

    private static final Logger log = LoggerFactory.getLogger(BankApplicantSeeder.class);

    private final ClientRepository clientRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final UnderwritingService underwritingService;

    public BankApplicantSeeder(ClientRepository clientRepository,
            LoanApplicationRepository loanApplicationRepository, UnderwritingService underwritingService) {
        this.clientRepository = clientRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.underwritingService = underwritingService;
    }

    /** One applicant to seed. {@code clientExternalId} is {@code null} for a synthetic (unlinked) filler. */
    private record ApplicantSpec(String seedKey, String applicantName, String initials, String purpose,
            BigDecimal amount, String clientExternalId) {
    }

    // The seeded queue. Linked specs target seeded personas; the three bands are reached by sizing the ask.
    private static final List<ApplicantSpec> SPECS = List.of(
            // Linked, healthy persona + small ask → OK band. (client_005_vip — the §11 bank demo applicant.)
            new ApplicantSpec("APP-VIP-EXPANSION", "Khalid Al-Otaibi", "KO",
                    "SME expansion facility", new BigDecimal("120000.00"), "client_005_vip"),
            // Linked, healthy persona + mid ask → WARN band.
            new ApplicantSpec("APP-FAMILY-AUTO", "Nasser Al-Harbi", "NH",
                    "Auto finance", new BigDecimal("260000.00"), "client_001_family"),
            // Linked, oversized ask → DTI past 71% → guaranteed BAD band.
            new ApplicantSpec("APP-STUDENT-TUITION", "Rana Al-Zahrani", "RZ",
                    "Tuition & living top-up", new BigDecimal("900000.00"), "client_004_student"),
            // Linked, irregular-income persona → cash-flow bridge.
            new ApplicantSpec("APP-FREELANCER-CASHFLOW", "Yousef Al-Dossari", "YD",
                    "Working-capital bridge", new BigDecimal("150000.00"), "client_003_freelancer"),
            // Linked, warning-zone persona → consolidation.
            new ApplicantSpec("APP-TECH-CONSOLIDATE", "Maha Al-Qahtani", "MQ",
                    "Debt consolidation", new BigDecimal("80000.00"), "client_002_tech_bro"),
            // Synthetic fillers (no linked telemetry) — sit un-underwritten in the pipeline.
            new ApplicantSpec("APP-SYN-RENOVATION", "Salem Al-Mutairi", "SM",
                    "Home renovation", new BigDecimal("45000.00"), null),
            new ApplicantSpec("APP-SYN-WEDDING", "Lina Al-Shammari", "LS",
                    "Wedding financing", new BigDecimal("30000.00"), null));

    @EventListener(ApplicationReadyEvent.class)
    public void seedApplicants() {
        int created = 0;
        int underwritten = 0;
        for (ApplicantSpec spec : SPECS) {
            if (loanApplicationRepository.existsBySeedKey(spec.seedKey())) {
                continue; // idempotent: already seeded
            }

            UUID clientRef = resolveClientRef(spec);
            if (spec.clientExternalId() != null && clientRef == null) {
                log.warn("Skipping applicant '{}' — linked persona '{}' is not seeded.",
                        spec.seedKey(), spec.clientExternalId());
                continue;
            }

            LoanApplication saved = loanApplicationRepository.save(new LoanApplication(
                    spec.seedKey(), spec.applicantName(), spec.initials(), spec.purpose(),
                    spec.amount(), clientRef));
            created++;

            // Underwrite linked applicants now so the pipeline shows a verdict at rest; leave synthetic
            // fillers un-scored until a banker requests a report.
            if (clientRef != null) {
                UnderwritingReport report = underwritingService.generateReport(saved.getId());
                underwritten++;
                log.info("Seeded & underwrote applicant '{}' ({}): stamina={}, DTI={}%, verdict={}.",
                        spec.seedKey(), spec.applicantName(), report.staminaScore(),
                        report.forecastDti().toPlainString(), report.verdict());
            } else {
                log.info("Seeded synthetic applicant '{}' ({}) — awaiting underwriting.",
                        spec.seedKey(), spec.applicantName());
            }
        }
        log.info("Bank applicant seeding complete: {} new applicant(s), {} underwritten.",
                created, underwritten);
    }

    /** Resolve a spec's linked persona to its client UUID, or {@code null} for a synthetic applicant. */
    private UUID resolveClientRef(ApplicantSpec spec) {
        if (spec.clientExternalId() == null) {
            return null;
        }
        return clientRepository.findByExternalId(spec.clientExternalId())
                .map(Client::getId)
                .orElse(null);
    }
}
