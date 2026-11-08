package com.finantrack.card.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "credit_card_installments")
public class CreditCardInstallment extends BaseEntity {

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private UUID purchaseId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    protected CreditCardInstallment() {
    }

    public CreditCardInstallment(UUID purchaseId, UUID invoiceId, UUID transactionId, int number, BigDecimal amount) {
        this.purchaseId = purchaseId;
        this.invoiceId = invoiceId;
        this.transactionId = transactionId;
        this.number = number;
        this.amount = amount;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getNumber() {
        return number;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
