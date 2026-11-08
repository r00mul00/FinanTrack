package com.finantrack.recurring.entity;

import com.finantrack.common.entity.BaseEntity;
import com.finantrack.transaction.entity.TransactionType;
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
@Table(name = "recurring_transactions")
public class RecurringTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RecurrenceFrequency frequency;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Cursor do próximo ciclo: só avança, por isso lançamento excluído não é recriado. */
    @Column(name = "next_occurrence_date")
    private LocalDate nextOccurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurringStatus status = RecurringStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    protected RecurringTransaction() {
    }

    public RecurringTransaction(UUID userId, String description, BigDecimal amount, TransactionType type,
            UUID categoryId, UUID accountId, RecurrenceFrequency frequency, LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.accountId = accountId;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextOccurrenceDate = startDate;
    }

    public void update(String description, BigDecimal amount, TransactionType type, UUID categoryId, UUID accountId,
            RecurrenceFrequency frequency, LocalDate endDate) {
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.accountId = accountId;
        this.frequency = frequency;
        this.endDate = endDate;
    }

    public void moveCursorTo(LocalDate next) {
        this.nextOccurrenceDate = next;
    }

    public void pause() {
        this.status = RecurringStatus.PAUSED;
    }

    public void activate() {
        this.status = RecurringStatus.ACTIVE;
    }

    public void finish() {
        this.status = RecurringStatus.FINISHED;
        this.nextOccurrenceDate = null;
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getNextOccurrenceDate() {
        return nextOccurrenceDate;
    }

    public RecurringStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
