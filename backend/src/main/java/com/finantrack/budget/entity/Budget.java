package com.finantrack.budget.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** Sempre o primeiro dia do mês. */
    @Column(name = "month", nullable = false, updatable = false)
    private LocalDate referenceMonth;

    @Column(name = "limit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal limitAmount;

    protected Budget() {
    }

    public Budget(UUID userId, UUID categoryId, LocalDate referenceMonth, BigDecimal limitAmount) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.referenceMonth = referenceMonth;
        this.limitAmount = limitAmount;
    }

    public void changeLimit(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
