package com.finantrack.recurring.dto;

import com.finantrack.recurring.entity.RecurrenceFrequency;
import com.finantrack.recurring.entity.RecurringStatus;
import com.finantrack.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringResponse(
        UUID id,
        String description,
        BigDecimal amount,
        TransactionType type,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        UUID accountId,
        String accountName,
        RecurrenceFrequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextOccurrenceDate,
        RecurringStatus status,
        long version) {
}
