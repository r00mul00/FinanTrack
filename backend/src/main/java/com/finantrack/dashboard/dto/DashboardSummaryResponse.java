package com.finantrack.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** As variações percentuais são nulas quando o período anterior é zero. */
public record DashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        BigDecimal totalBalance,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal result,
        BigDecimal previousIncome,
        BigDecimal previousExpenses,
        BigDecimal previousResult,
        BigDecimal incomeChange,
        BigDecimal expensesChange,
        BigDecimal resultChange) {
}
