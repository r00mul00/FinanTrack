package com.finantrack.card.service;

import java.time.LocalDate;
import java.time.YearMonth;

/** Regras de datas de fatura de docs/06. Dia inexistente no mês usa o último dia. */
public final class InvoiceCalendar {

    private InvoiceCalendar() {
    }

    /** Compra até o dia do fechamento, inclusive, entra na fatura do mês; depois, na seguinte. */
    public static YearMonth referenceMonthOf(LocalDate purchaseDate, int closingDay) {
        YearMonth month = YearMonth.from(purchaseDate);
        return purchaseDate.isAfter(closingDate(month, closingDay)) ? month.plusMonths(1) : month;
    }

    public static LocalDate closingDate(YearMonth referenceMonth, int closingDay) {
        return dayOf(referenceMonth, closingDay);
    }

    /** Vencimento depois do fechamento fica no mesmo mês; caso contrário, no seguinte. */
    public static LocalDate dueDate(YearMonth referenceMonth, int closingDay, int dueDay) {
        return dayOf(dueDay > closingDay ? referenceMonth : referenceMonth.plusMonths(1), dueDay);
    }

    private static LocalDate dayOf(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
