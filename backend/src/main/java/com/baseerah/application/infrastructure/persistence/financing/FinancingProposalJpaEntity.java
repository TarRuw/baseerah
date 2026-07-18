package com.baseerah.application.infrastructure.persistence.financing;

import com.baseerah.domain.financing.ProposalStatus;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One bank's proposal within a financing request — a row of {@code financing_proposals}. Persistence-only:
 * projected to the domain {@link com.baseerah.domain.financing.FinancingProposal} by
 * {@code FinancingPersistenceMapper}, never crossing the web boundary.
 *
 * <p>Created {@link ProposalStatus#PENDING} with null {@code rate}/{@code termMonths}; the bank operator's
 * reply stamps those and flips the status to {@code REPLIED} (or {@code DECLINED}). {@code chosen} flips true
 * on the single proposal the consumer accepts.
 */
@Entity
@Table(name = "financing_proposals")
public class FinancingProposalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private FinancingRequestJpaEntity request;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "chosen", nullable = false)
    private boolean chosen;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FinancingProposalJpaEntity() {
        // JPA
    }

    public FinancingProposalJpaEntity(String bankName, BigDecimal amount) {
        this.bankName = bankName;
        this.amount = amount;
    }

    /** Record the bank operator's reply: the profit rate, term, and a REPLIED status. */
    public void applyReply(BigDecimal rate, Integer termMonths, Instant repliedAt) {
        this.rate = rate;
        this.termMonths = termMonths;
        this.repliedAt = repliedAt;
        this.status = ProposalStatus.REPLIED;
    }

    /** The consumer accepted this offer's terms — it now awaits the bank's disbursement. */
    public void accept() {
        this.chosen = true;
        this.status = ProposalStatus.ACCEPTED;
    }

    /** The bank disbursed the facility: stamp the funding time and first repayment due date, mark ACTIVE. */
    public void disburse(Instant disbursedAt, LocalDate firstPaymentDate) {
        this.disbursedAt = disbursedAt;
        this.firstPaymentDate = firstPaymentDate;
        this.status = ProposalStatus.DISBURSED;
    }

    public UUID getId() {
        return id;
    }

    public FinancingRequestJpaEntity getRequest() {
        return request;
    }

    void setRequest(FinancingRequestJpaEntity request) {
        this.request = request;
    }

    public String getBankName() {
        return bankName;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isChosen() {
        return chosen;
    }

    public void setChosen(boolean chosen) {
        this.chosen = chosen;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public LocalDate getFirstPaymentDate() {
        return firstPaymentDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
