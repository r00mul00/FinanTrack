package com.finantrack.goal.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_goals")
public class FinancialGoal extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "initial_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialAmount;

    @Column(name = "current_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentAmount;

    @Column(nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FinancialGoal() {
    }

    public FinancialGoal(UUID userId, String name, String description, BigDecimal targetAmount,
            BigDecimal initialAmount, LocalDate deadline) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.targetAmount = targetAmount;
        this.initialAmount = initialAmount;
        this.currentAmount = initialAmount;
        this.deadline = deadline;
    }

    /** Mudar o valor inicial desloca o saldo pela mesma diferença, preservando as movimentações. */
    public void update(String name, String description, BigDecimal targetAmount, BigDecimal initialAmount,
            LocalDate deadline) {
        this.currentAmount = currentAmount.add(initialAmount.subtract(this.initialAmount));
        this.name = name;
        this.description = description;
        this.targetAmount = targetAmount;
        this.initialAmount = initialAmount;
        this.deadline = deadline;
    }

    public void deposit(BigDecimal amount) {
        this.currentAmount = currentAmount.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        this.currentAmount = currentAmount.subtract(amount);
    }

    public void complete(Instant when) {
        this.status = GoalStatus.COMPLETED;
        this.completedAt = when;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public BigDecimal getInitialAmount() {
        return initialAmount;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
