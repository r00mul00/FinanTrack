package com.finantrack.recurring.service;

import com.finantrack.recurring.entity.RecurrenceFrequency;
import java.time.LocalDate;

/**
 * Cada ciclo é calculado a partir do início, não do ciclo anterior: assim uma
 * recorrência no dia 31 usa 28/02 em fevereiro e volta a 31/03 em março.
 */
public final class RecurrenceCalendar {

    private RecurrenceCalendar() {
    }

    public static LocalDate occurrence(LocalDate start, RecurrenceFrequency frequency, int index) {
        return switch (frequency) {
            case WEEKLY -> start.plusWeeks(index);
            case BIWEEKLY -> start.plusWeeks(2L * index);
            case MONTHLY -> start.plusMonths(index);
            case QUARTERLY -> start.plusMonths(3L * index);
            case YEARLY -> start.plusYears(index);
        };
    }

    public static LocalDate firstOnOrAfter(LocalDate start, RecurrenceFrequency frequency, LocalDate date) {
        int index = 0;
        LocalDate candidate = start;
        while (candidate.isBefore(date)) {
            candidate = occurrence(start, frequency, ++index);
        }
        return candidate;
    }

    public static LocalDate firstAfter(LocalDate start, RecurrenceFrequency frequency, LocalDate date) {
        return firstOnOrAfter(start, frequency, date.plusDays(1));
    }
}
