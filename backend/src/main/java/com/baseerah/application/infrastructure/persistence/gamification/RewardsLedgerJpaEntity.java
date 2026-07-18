package com.baseerah.application.infrastructure.persistence.gamification;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One append-only entry in a client's Akhtar-Points journal (DESIGN.md §4.2, §5.6). The client's balance is
 * the running sum of {@code pointsDelta} across their rows — there is no denormalised balance column, so the
 * ledger is the single source of truth and every award is auditable ({@code reason} records why). Today only
 * challenge claims write positive deltas; the signed column leaves room for future debits (redemptions).
 *
 * <p>A JPA type confined to the persistence package; never crosses a layer boundary. The {@code @ManyToOne}
 * target is still the pre-10.9 {@link ClientJpaEntity} entity.
 */
@Entity
@Table(name = "rewards_ledger")
public class RewardsLedgerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Column(name = "reason")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RewardsLedgerJpaEntity() {
        // JPA
    }

    public RewardsLedgerJpaEntity(ClientJpaEntity client, int pointsDelta, String reason) {
        this.client = client;
        this.pointsDelta = pointsDelta;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
        return client;
    }

    public int getPointsDelta() {
        return pointsDelta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
