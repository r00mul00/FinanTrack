package com.finantrack.dashboard.service;

import com.finantrack.common.exception.InvalidParameterException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Período consultado e o período anterior usado na comparação. */
public record DashboardPeriod(LocalDate from, LocalDate to) {

    private static final int MAX_YEARS = 5;

    /** Sem datas, usa o mês corrente. */
    public static DashboardPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null && to == null) {
            YearMonth month = YearMonth.from(today);
            return new DashboardPeriod(month.atDay(1), month.atEndOfMonth());
        }
        if (from == null || to == null) {
            throw new InvalidParameterException("Informe as datas inicial e final.");
        }
        if (from.isAfter(to)) {
            throw new InvalidParameterException("A data inicial não pode ser posterior à data final.");
        }
        if (from.plusYears(MAX_YEARS).isBefore(to)) {
            throw new InvalidParameterException("O período não pode ser maior que 5 anos.");
        }
        return new DashboardPeriod(from, to);
    }

    /** Mês completo compara com o mês anterior; os demais, com o mesmo número de dias logo antes. */
    DashboardPeriod previous() {
        YearMonth month = YearMonth.from(from);
        if (from.equals(month.atDay(1)) && to.equals(month.atEndOfMonth())) {
            YearMonth previous = month.minusMonths(1);
            return new DashboardPeriod(previous.atDay(1), previous.atEndOfMonth());
        }
        long days = lengthInDays();
        return new DashboardPeriod(from.minusDays(days), from.minusDays(1));
    }

    public long lengthInDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
