package com.baseerah.bank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A bank-side loan applicant and the predictive report computed for them (FR-08, DESIGN.md §4.2, §5.5).
 * One row of {@code loan_applications}: it carries both the <em>request</em> (applicant identity, purpose,
 * requested {@code amount}, and an optional {@code clientRef} linking to a seeded persona's real telemetry)
 * and the <em>report</em> the {@link UnderwritingService} stamps back onto it (stamina, forecast DTI, income
 * stability, 12-month default probability, {@link Verdict}, risk tier), plus the eventual human
 * {@link Decision}.
 *
 * <p>Persistence-only: like every entity here it never leaves the service layer — Step 6.2 maps it (and the
 * richer {@link UnderwritingReport}) to wire DTOs. The report fields are nullable so a freshly-seeded
 * synthetic applicant can sit in the queue un-underwritten (all report fields {@code null}) until a report
 * is generated; {@code decision} stays {@code null} until a banker acts. {@code seedKey} is a stable
 * idempotency handle for {@link BankApplicantSeeder} (unique in the schema) and is {@code null} for any
 * applicant created at runtime.
 *
 * <p>{@code clientRef} is stored as the raw client UUID rather than a JPA {@code @ManyToOne} association: an
 * applicant may be synthetic (unlinked), the bank side deliberately holds only a tokenized reference to the
 * consumer (DESIGN §9 — the bank never navigates into a consumer's object graph), and the underwriting
 * service resolves telemetry explicitly through the repositories. Money and ratio fields are
 * {@code numeric(14,2)}; the three ratio fields ({@code forecastDti}, {@code incomeStability},
 * {@code defaultProb12mo}) are stored as <strong>percentages</strong> (e.g. {@code 34.00} = 34%) — see
 * {@link UnderwritingService} for the derivations.
 */
@Entity
@Table(name = "loan_applications")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "seed_key", unique = true)
    private String seedKey;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "initials")
    private String initials;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "client_ref")
    private UUID clientRef;

    @Column(name = "stamina_score")
    private Integer staminaScore;

    @Column(name = "forecast_dti")
    private BigDecimal forecastDti;

    @Column(name = "income_stability")
    private BigDecimal incomeStability;

    @Column(name = "default_prob_12mo")
    private BigDecimal defaultProb12mo;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    private Verdict verdict;

    @Column(name = "risk_tier")
    private String riskTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private Decision decision;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoanApplication() {
        // JPA
    }

    /** Create a queued applicant (request side only); the report fields are populated later by underwriting. */
    public LoanApplication(String seedKey, String applicantName, String initials, String purpose,
            BigDecimal amount, UUID clientRef) {
        this.seedKey = seedKey;
        this.applicantName = applicantName;
        this.initials = initials;
        this.purpose = purpose;
        this.amount = amount;
        this.clientRef = clientRef;
    }

    /**
     * Stamp a computed report's fields onto this application (called by {@link UnderwritingService} after it
     * computes the §5.5 metrics). Leaves the human {@link #decision} untouched.
     */
    public void applyReport(int staminaScore, BigDecimal forecastDti, BigDecimal incomeStability,
            BigDecimal defaultProb12mo, Verdict verdict, String riskTier) {
        this.staminaScore = staminaScore;
        this.forecastDti = forecastDti;
        this.incomeStability = incomeStability;
        this.defaultProb12mo = defaultProb12mo;
        this.verdict = verdict;
        this.riskTier = riskTier;
    }

    public UUID getId() {
        return id;
    }

    public String getSeedKey() {
        return seedKey;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public String getInitials() {
        return initials;
    }

    public String getPurpose() {
        return purpose;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getClientRef() {
        return clientRef;
    }

    public Integer getStaminaScore() {
        return staminaScore;
    }

    public BigDecimal getForecastDti() {
        return forecastDti;
    }

    public BigDecimal getIncomeStability() {
        return incomeStability;
    }

    public BigDecimal getDefaultProb12mo() {
        return defaultProb12mo;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getRiskTier() {
        return riskTier;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
