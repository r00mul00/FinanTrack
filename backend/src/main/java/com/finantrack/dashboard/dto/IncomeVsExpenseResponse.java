package com.finantrack.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IncomeVsExpenseResponse(Granularity granularity, List<Point> points) {

    public enum Granularity {
        DAY,
        MONTH
    }

    /** Em MONTH, a data é o primeiro dia do mês. */
    public record Point(LocalDate date, BigDecimal income, BigDecimal expenses) {
    }
}
