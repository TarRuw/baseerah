package com.baseerah.application.infrastructure.persistence.stress;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.domain.stress.Zone;
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
 * A daily snapshot of a client's Financial Stress Score (DESIGN.md §4.2, §5.1). One row per client per
 * day — enforced by the {@code (client_id, as_of_date)} unique key — written by the application shell
 * {@code StressScoreSnapshotWriter}. Persistence-only: it never leaves the application layer; the service
 * maps it to the domain {@link com.baseerah.domain.stress.StressScore} via {@link StressPersistenceMapper}.
 *
 * <p>{@code score} is the integer 0–100 index; {@code spendingVelocity}, {@code incomeConsistency} and
 * {@code liabilityRatio} hold the three sub-scores on the same 0–100 healthiness scale (higher = better)
 * for the radar chart. (Its {@code @ManyToOne} target remains the {@code ClientJpaEntity} entity until the client
 * CRUD slice, step 10.9.)
 */
@Entity
@Table(name = "stress_scores")
public class StressScoreJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "score", nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    private Zone zone;

    @Column(name = "spending_velocity", nullable = false)
    private BigDecimal spendingVelocity;

    @Column(name = "income_consistency", nullable = false)
    private BigDecimal incomeConsistency;

    @Column(name = "liability_ratio", nullable = false)
    private BigDecimal liabilityRatio;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StressScoreJpaEntity() {
        // JPA
    }

    public StressScoreJpaEntity(ClientJpaEntity client, LocalDate asOfDate, int score, Zone zone,
            BigDecimal spendingVelocity, BigDecimal incomeConsistency, BigDecimal liabilityRatio) {
        this.client = client;
        this.asOfDate = asOfDate;
        this.score = score;
        this.zone = zone;
        this.spendingVelocity = spendingVelocity;
        this.incomeConsistency = incomeConsistency;
        this.liabilityRatio = liabilityRatio;
    }

    /** Overwrite the computed values in place — used by the writer's daily upsert. */
    public void update(int score, Zone zone, BigDecimal spendingVelocity,
            BigDecimal incomeConsistency, BigDecimal liabilityRatio) {
        this.score = score;
        this.zone = zone;
        this.spendingVelocity = spendingVelocity;
        this.incomeConsistency = incomeConsistency;
        this.liabilityRatio = liabilityRatio;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
        return client;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public int getScore() {
        return score;
    }

    public Zone getZone() {
        return zone;
    }

    public BigDecimal getSpendingVelocity() {
        return spendingVelocity;
    }

    public BigDecimal getIncomeConsistency() {
        return incomeConsistency;
    }

    public BigDecimal getLiabilityRatio() {
        return liabilityRatio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
