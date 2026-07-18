package com.baseerah.application.infrastructure.persistence.financing;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.domain.bank.Verdict;
import com.baseerah.domain.financing.FinancingOrigin;
import com.baseerah.domain.financing.FinancingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A consumer financing request (RFP) and its per-bank proposals — one row of {@code financing_requests} with
 * a {@code @OneToMany} of {@link FinancingProposalJpaEntity}. Persistence-only: it never leaves the
 * application layer; {@code FinancingPersistenceMapper} projects it to the domain {@code FinancingRequest}.
 *
 * <p>The {@code @ManyToOne} client mirrors {@code RescueEventJpaEntity} (the rescue slice this feature
 * extends). Proposals cascade-persist with the request so creating the RFP writes the whole fan-out in one
 * save; {@code deficitInDays} is captured at creation so the audited {@code rescue_events} row written on
 * choose carries the real lead time without re-running the forecast.
 */
@Entity
@Table(name = "financing_requests")
public class FinancingRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "deficit_in_days", nullable = false)
    private int deficitInDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinancingStatus status = FinancingStatus.OPEN;

    @Column(name = "purpose")
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private FinancingOrigin origin = FinancingOrigin.RESCUE;

    // The per-request underwriting report (Phase 12). Nullable — populated by the underwriting service (Step
    // 12.2) once a banker underwrites the request; a fresh request sits with all report fields null.
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

    @OneToMany(mappedBy = "request", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<FinancingProposalJpaEntity> proposals = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FinancingRequestJpaEntity() {
        // JPA
    }

    public FinancingRequestJpaEntity(ClientJpaEntity client, BigDecimal amount, int deficitInDays) {
        this.client = client;
        this.amount = amount;
        this.deficitInDays = deficitInDays;
    }

    /** Attach a proposal to this request, wiring the back-reference so the cascade persists it. */
    public void addProposal(FinancingProposalJpaEntity proposal) {
        proposal.setRequest(this);
        this.proposals.add(proposal);
    }

    /**
     * Stamp a computed underwriting report's fields onto this request (Phase 12 — mirrors the retired
     * {@code LoanApplicationJpaEntity.applyReport}). Called by the underwriting service (Step 12.2) after it
     * computes the §5.5 metrics; leaves lifecycle {@link #status} and {@link #proposals} untouched.
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

    public ClientJpaEntity getClient() {
        return client;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getDeficitInDays() {
        return deficitInDays;
    }

    public FinancingStatus getStatus() {
        return status;
    }

    public void setStatus(FinancingStatus status) {
        this.status = status;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public FinancingOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(FinancingOrigin origin) {
        this.origin = origin;
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

    public List<FinancingProposalJpaEntity> getProposals() {
        return proposals;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
