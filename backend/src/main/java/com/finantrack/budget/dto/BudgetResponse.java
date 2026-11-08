package com.finantrack.budget.dto;

import com.finantrack.budget.entity.BudgetState;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        String month,
        BigDecimal limitAmount,
        BigDecimal usedAmount,
        BigDecimal remainingAmount,
        BigDecimal percentage,
        BudgetState state) {
}
