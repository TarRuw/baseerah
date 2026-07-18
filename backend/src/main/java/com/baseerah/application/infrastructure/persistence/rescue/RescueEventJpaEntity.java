package com.baseerah.application.infrastructure.persistence.rescue;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.domain.rescue.RescueOptionType;
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
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A confirmed Smart Rescue, appended to {@code rescue_events} (DESIGN.md §4.2, §5.4) each time a client
 * commits to a bridge option. It records what triggered the rescue ({@code predictedShortfall},
 * {@code deficitInDays}), which bridge was chosen, and the before/after stress score so the recovery is
 * auditable. Persistence-only: it never leaves the application layer — {@code RescueEventPersistenceMapper}
 * builds it from domain values and the service persists it via {@code RescueEventRepository}.
 *
 * <p>{@code optionChosen} is stored as text and constrained by the migration's
 * {@code chk_rescue_option_chosen} CHECK; it is nullable in the schema to allow a logged-but-unconfirmed
 * assessment, though {@code RescueService.confirm} always sets it. The {@code @ManyToOne} keeps the existing
 * {@code ClientJpaEntity} entity until the CRUD slice (step 10.9) relocates it.
 */
@Entity
@Table(name = "rescue_events")
public class RescueEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "predicted_shortfall", nullable = false)
    private BigDecimal predictedShortfall;

    @Column(name = "deficit_in_days", nullable = false)
    private int deficitInDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_chosen")
    private RescueOptionType optionChosen;

    @Column(name = "score_before", nullable = false)
    private int scoreBefore;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RescueEventJpaEntity() {
        // JPA
    }

    public RescueEventJpaEntity(ClientJpaEntity client, BigDecimal predictedShortfall, int deficitInDays,
            RescueOptionType optionChosen, int scoreBefore, int scoreAfter) {
        this.client = client;
        this.predictedShortfall = predictedShortfall;
        this.deficitInDays = deficitInDays;
        this.optionChosen = optionChosen;
        this.scoreBefore = scoreBefore;
        this.scoreAfter = scoreAfter;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
        return client;
    }

    public BigDecimal getPredictedShortfall() {
        return predictedShortfall;
    }

    public int getDeficitInDays() {
        return deficitInDays;
    }

    public RescueOptionType getOptionChosen() {
        return optionChosen;
    }

    public int getScoreBefore() {
        return scoreBefore;
    }

    public int getScoreAfter() {
        return scoreAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
