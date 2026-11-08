package com.finantrack.transaction.dto;

import com.finantrack.transaction.entity.TransactionKind;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String description,
        BigDecimal amount,
        TransactionType type,
        TransactionKind kind,
        TransactionStatus status,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        UUID accountId,
        String accountName,
        LocalDate transactionDate,
        LocalDate dueDate,
        String notes,
        long version) {
}
