package com.finantrack.transaction.service;

import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Campos auditados de uma movimentação. As observações, texto livre, ficam de fora. */
public record TransactionSnapshot(String description, BigDecimal amount, TransactionType type, TransactionStatus status,
        UUID categoryId, UUID accountId, LocalDate transactionDate, LocalDate dueDate) {

    public static TransactionSnapshot of(Transaction item) {
        return new TransactionSnapshot(item.getDescription(), item.getAmount(), item.getType(), item.getStatus(),
                item.getCategoryId(), item.getAccountId(), item.getTransactionDate(), item.getDueDate());
    }
}
