package com.baseerah.transaction;

import com.baseerah.account.Account;
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
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single booked transaction on an {@link Account}. Maps the {@code transactions} table (DESIGN.md §4.2).
 *
 * <p>{@code category} deliberately persists the <em>raw</em> feed string (lossless) rather than a
 * constrained enum; use {@link #resolveCategory()} / {@link Category#resolve(String)} for a typed view
 * in scoring and analytics. {@code direction} is a closed set, so it is mapped as a {@link Direction}
 * enum via {@link EnumType#STRING}.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "raw_description")
    private String rawDescription;

    @Column(name = "description_cleansed")
    private String descriptionCleansed;

    @Column(name = "category")
    private String category;

    @Column(name = "category_confidence")
    private BigDecimal categoryConfidence;

    @Column(name = "booking_date", nullable = false)
    private Instant bookingDate;

    @Column(name = "closing_balance")
    private BigDecimal closingBalance;

    protected Transaction() {
        // JPA
    }

    public Transaction(Account account, String externalId, Direction direction, BigDecimal amount,
            String currency, String rawDescription, String descriptionCleansed, String category,
            BigDecimal categoryConfidence, Instant bookingDate, BigDecimal closingBalance) {
        this.account = account;
        this.externalId = externalId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.rawDescription = rawDescription;
        this.descriptionCleansed = descriptionCleansed;
        this.category = category;
        this.categoryConfidence = categoryConfidence;
        this.bookingDate = bookingDate;
        this.closingBalance = closingBalance;
    }

    /** Typed view of the raw {@link #category} string, falling back to {@link Category#OTHER}. */
    @Transient
    public Category resolveCategory() {
        return Category.resolve(category);
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
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

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRawDescription() {
        return rawDescription;
    }

    public void setRawDescription(String rawDescription) {
        this.rawDescription = rawDescription;
    }

    public String getDescriptionCleansed() {
        return descriptionCleansed;
    }

    public void setDescriptionCleansed(String descriptionCleansed) {
        this.descriptionCleansed = descriptionCleansed;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getCategoryConfidence() {
        return categoryConfidence;
    }

    public void setCategoryConfidence(BigDecimal categoryConfidence) {
        this.categoryConfidence = categoryConfidence;
    }

    public Instant getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(Instant bookingDate) {
        this.bookingDate = bookingDate;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }
}
