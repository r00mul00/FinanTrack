package com.finantrack.transaction.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionKind kind = TransactionKind.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "invoice_id", updatable = false)
    private UUID invoiceId;

    @Column(name = "recurring_id", updatable = false)
    private UUID recurringId;

    @Column(name = "occurrence_date", updatable = false)
    private LocalDate occurrenceDate;

    @Version
    @Column(nullable = false)
    private long version;

    protected Transaction() {
    }

    public Transaction(UUID userId, Details details) {
        this.userId = userId;
        apply(details);
    }

    /** Lançamento gerado por uma recorrência: nasce pendente, vencendo na data do ciclo. */
    public static Transaction recurringOccurrence(UUID userId, UUID recurringId, LocalDate occurrenceDate, Details details) {
        Transaction transaction = new Transaction(userId, details);
        transaction.recurringId = recurringId;
        transaction.occurrenceDate = occurrenceDate;
        return transaction;
    }

    /** Parcela de cartão: não movimenta conta; fica pendente até a fatura ser quitada. */
    public static Transaction cardInstallment(UUID userId, String description, BigDecimal amount, UUID categoryId,
            LocalDate date) {
        Transaction transaction = new Transaction(userId, new Details(description, amount, TransactionType.EXPENSE,
                TransactionStatus.PENDING, categoryId, null, date, null, null));
        transaction.kind = TransactionKind.CARD_INSTALLMENT;
        return transaction;
    }

    /** Pagamento de fatura: reduz o saldo da conta, mas não é uma nova despesa. */
    public static Transaction invoicePayment(UUID userId, String description, BigDecimal amount, UUID accountId,
            UUID invoiceId, LocalDate date) {
        Transaction transaction = new Transaction(userId, new Details(description, amount, TransactionType.EXPENSE,
                TransactionStatus.COMPLETED, null, accountId, date, null, null));
        transaction.kind = TransactionKind.INVOICE_PAYMENT;
        transaction.invoiceId = invoiceId;
        return transaction;
    }

    public void update(Details details) {
        apply(details);
    }

    public void complete() {
        this.status = TransactionStatus.COMPLETED;
    }

    private void apply(Details details) {
        this.description = details.description();
        this.amount = details.amount();
        this.type = details.type();
        this.status = details.status();
        this.categoryId = details.categoryId();
        this.accountId = details.accountId();
        this.transactionDate = details.transactionDate();
        this.dueDate = details.dueDate();
        this.notes = details.notes();
    }

    public record Details(
            String description,
            BigDecimal amount,
            TransactionType type,
            TransactionStatus status,
            UUID categoryId,
            UUID accountId,
            LocalDate transactionDate,
            LocalDate dueDate,
            String notes) {
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionKind getKind() {
        return kind;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isRecurring() {
        return recurringId != null;
    }

    public long getVersion() {
        return version;
    }
}
