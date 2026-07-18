package com.baseerah.application.infrastructure.persistence.account;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A bank account belonging to a {@link ClientJpaEntity}. Maps the {@code accounts} table (DESIGN.md §4.2).
 * {@code latestBalance} is a denormalised convenience column; the authoritative balance history lives
 * in {@link TransactionJpaEntity#getClosingBalance()}.
 */
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "display_color")
    private String displayColor;

    @Column(name = "currency")
    private String currency;

    @Column(name = "latest_balance")
    private BigDecimal latestBalance;

    @Column(name = "tokenized_account_id")
    private String tokenizedAccountId;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionJpaEntity> transactions = new ArrayList<>();

    protected AccountJpaEntity() {
        // JPA
    }

    public AccountJpaEntity(ClientJpaEntity client, String externalId, String bankName, String displayColor,
            String currency, BigDecimal latestBalance, String tokenizedAccountId) {
        this.client = client;
        this.externalId = externalId;
        this.bankName = bankName;
        this.displayColor = displayColor;
        this.currency = currency;
        this.latestBalance = latestBalance;
        this.tokenizedAccountId = tokenizedAccountId;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
        return client;
    }

    public void setClient(ClientJpaEntity client) {
        this.client = client;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = displayColor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getLatestBalance() {
        return latestBalance;
    }

    public void setLatestBalance(BigDecimal latestBalance) {
        this.latestBalance = latestBalance;
    }

    public String getTokenizedAccountId() {
        return tokenizedAccountId;
    }

    public void setTokenizedAccountId(String tokenizedAccountId) {
        this.tokenizedAccountId = tokenizedAccountId;
    }

    public List<TransactionJpaEntity> getTransactions() {
        return transactions;
    }
}
