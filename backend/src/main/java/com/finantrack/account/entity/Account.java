package com.finantrack.account.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(name = "initial_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialBalance;

    @Column(nullable = false)
    private boolean archived;

    protected Account() {
    }

    public Account(UUID userId, String name, AccountType type, BigDecimal initialBalance) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
    }

    public void update(String name, AccountType type, BigDecimal initialBalance, boolean archived) {
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
        this.archived = archived;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public boolean isArchived() {
        return archived;
    }
}
