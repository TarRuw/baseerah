package com.baseerah.rescue;

import com.baseerah.client.Client;
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
 * auditable. Persistence-only: like the other entities it never leaves the service layer — Step 4.2 maps
 * it to a DTO.
 *
 * <p>{@code optionChosen} is stored as text and constrained by the migration's
 * {@code chk_rescue_option_chosen} CHECK; it is nullable in the schema to allow a logged-but-unconfirmed
 * assessment, though {@link RescueService#confirm} always sets it.
 */
@Entity
@Table(name = "rescue_events")
public class RescueEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

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

    protected RescueEvent() {
        // JPA
    }

    public RescueEvent(Client client, BigDecimal predictedShortfall, int deficitInDays,
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

    public Client getClient() {
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
