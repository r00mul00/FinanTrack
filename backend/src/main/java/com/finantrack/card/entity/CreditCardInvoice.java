package com.finantrack.card.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credit_card_invoices")
public class CreditCardInvoice extends BaseEntity {

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "reference_month", nullable = false, updatable = false)
    private LocalDate referenceMonth;

    @Column(name = "closing_date", nullable = false, updatable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    protected CreditCardInvoice() {
    }

    public CreditCardInvoice(UUID cardId, LocalDate referenceMonth, LocalDate closingDate, LocalDate dueDate) {
        this.cardId = cardId;
        this.referenceMonth = referenceMonth;
        this.closingDate = closingDate;
        this.dueDate = dueDate;
    }

    public void registerPayment(BigDecimal amount) {
        this.paidAmount = paidAmount.add(amount);
    }

    public UUID getCardId() {
        return cardId;
    }

    public LocalDate getReferenceMonth() {
        return referenceMonth;
    }

    public LocalDate getClosingDate() {
        return closingDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }
}
