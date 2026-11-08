package com.finantrack.card.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "credit_card_purchases")
public class CreditCardPurchase extends BaseEntity {

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, updatable = false, length = 180)
    private String description;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "installments_count", nullable = false, updatable = false)
    private int installmentsCount;

    @Column(name = "purchase_date", nullable = false, updatable = false)
    private LocalDate purchaseDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    protected CreditCardPurchase() {
    }

    public CreditCardPurchase(UUID cardId, UUID categoryId, String description, BigDecimal totalAmount,
            int installmentsCount, LocalDate purchaseDate) {
        this.cardId = cardId;
        this.categoryId = categoryId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.installmentsCount = installmentsCount;
        this.purchaseDate = purchaseDate;
    }

    public void cancel(Instant when) {
        this.canceledAt = when;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getInstallmentsCount() {
        return installmentsCount;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }
}
