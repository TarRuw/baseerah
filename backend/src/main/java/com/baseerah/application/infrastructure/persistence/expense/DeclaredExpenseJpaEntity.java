package com.baseerah.application.infrastructure.persistence.expense;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user-declared recurring expense, persisting the {@code declared_expenses} table (migration
 * {@code 015-declared-expenses.sql}, Phase 11). Hangs off {@link ClientJpaEntity} — not an account — because
 * the expense is cash the SAMA feed never sees. Persistence-only: it never leaves the application layer;
 * {@code DeclaredExpensePersistenceMapper} maps it to the pure {@code domain.expense.DeclaredExpense} the
 * calculators consume and to the JPA row the service writes, mirroring {@code RescueEventJpaEntity}.
 *
 * <p>{@code currency} (SAR) and {@code cadence} (MONTHLY) are single-valued for now and CHECK-pinned by the
 * migration; they are set once at construction and never mutated. {@code label}, {@code category},
 * {@code amount}, and {@code dayOfMonth} are the editable fields (Step 11.1 {@code PUT}); {@code active}
 * flips to {@code false} on soft-delete. {@code @UpdateTimestamp} bumps {@code updated_at} on every edit so a
 * {@code PUT} is observable without the service touching the clock.
 */
@Entity
@Table(name = "declared_expenses")
public class DeclaredExpenseJpaEntity {

    /** SAR-only product; the {@code currency} column is CHECK-pinned by the migration. */
    private static final String DEFAULT_CURRENCY = "SAR";

    /** MONTHLY-only cadence for now; the {@code cadence} column is CHECK-pinned by the migration. */
    private static final String DEFAULT_CADENCE = "MONTHLY";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "cadence", nullable = false)
    private String cadence;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeclaredExpenseJpaEntity() {
        // JPA
    }

    public DeclaredExpenseJpaEntity(ClientJpaEntity client, String label, String category, BigDecimal amount,
            int dayOfMonth) {
        this.client = client;
        this.label = label;
        this.category = category;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
        this.currency = DEFAULT_CURRENCY;
        this.cadence = DEFAULT_CADENCE;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
        return client;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCadence() {
        return cadence;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
