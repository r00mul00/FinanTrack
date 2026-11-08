package com.finantrack.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetMonthResponse(String month, BigDecimal totalLimit, BigDecimal totalUsed, List<BudgetResponse> items) {
}
