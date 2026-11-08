package com.finantrack.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceCalendarTest {

    @ParameterizedTest
    @CsvSource({
            "2026-03-14, 15, 2026-03",
            "2026-03-15, 15, 2026-03",
            "2026-03-16, 15, 2026-04",
            "2026-12-20, 15, 2027-01",
            "2027-02-28, 31, 2027-02",
            "2028-02-29, 31, 2028-02",
            "2027-02-28, 28, 2027-02",
            "2027-03-01, 31, 2027-03"
    })
    void purchaseGoesToExpectedInvoice(LocalDate purchase, int closingDay, YearMonth expected) {
        assertThat(InvoiceCalendar.referenceMonthOf(purchase, closingDay)).isEqualTo(expected);
    }

    @Test
    void closingDayUsesLastDayOfShortMonths() {
        assertThat(InvoiceCalendar.closingDate(YearMonth.of(2027, 2), 31)).isEqualTo("2027-02-28");
        assertThat(InvoiceCalendar.closingDate(YearMonth.of(2028, 2), 31)).isEqualTo("2028-02-29");
        assertThat(InvoiceCalendar.closingDate(YearMonth.of(2027, 4), 31)).isEqualTo("2027-04-30");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03, 5, 15, 2026-03-15",
            "2026-03, 25, 5, 2026-04-05",
            "2026-03, 10, 10, 2026-04-10",
            "2026-12, 25, 5, 2027-01-05",
            "2027-01, 25, 31, 2027-01-31",
            "2027-01, 31, 28, 2027-02-28"
    })
    void dueDateFollowsClosing(YearMonth month, int closingDay, int dueDay, LocalDate expected) {
        assertThat(InvoiceCalendar.dueDate(month, closingDay, dueDay)).isEqualTo(expected);
    }
}
