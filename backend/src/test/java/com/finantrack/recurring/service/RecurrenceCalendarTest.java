package com.finantrack.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.finantrack.recurring.entity.RecurrenceFrequency;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecurrenceCalendarTest {

    @ParameterizedTest
    @CsvSource({
            "2026-01-31, MONTHLY, 1, 2026-02-28",
            "2026-01-31, MONTHLY, 2, 2026-03-31",
            "2026-01-31, MONTHLY, 3, 2026-04-30",
            "2026-01-31, QUARTERLY, 1, 2026-04-30",
            "2028-02-29, YEARLY, 1, 2029-02-28",
            "2028-02-29, YEARLY, 4, 2032-02-29",
            "2026-01-01, WEEKLY, 2, 2026-01-15",
            "2026-01-01, BIWEEKLY, 2, 2026-01-29"
    })
    void occurrenceKeepsOriginalDay(LocalDate start, RecurrenceFrequency frequency, int index, LocalDate expected) {
        assertThat(RecurrenceCalendar.occurrence(start, frequency, index)).isEqualTo(expected);
    }

    @Test
    void firstOnOrAfterIncludesExactDate() {
        LocalDate start = LocalDate.parse("2026-01-10");

        assertThat(RecurrenceCalendar.firstOnOrAfter(start, RecurrenceFrequency.MONTHLY, LocalDate.parse("2026-03-10")))
                .isEqualTo("2026-03-10");
        assertThat(RecurrenceCalendar.firstOnOrAfter(start, RecurrenceFrequency.MONTHLY, LocalDate.parse("2026-03-11")))
                .isEqualTo("2026-04-10");
        assertThat(RecurrenceCalendar.firstOnOrAfter(start, RecurrenceFrequency.MONTHLY, LocalDate.parse("2025-01-01")))
                .isEqualTo(start);
    }

    @Test
    void firstAfterSkipsExactDateAndRespectsShortMonths() {
        LocalDate start = LocalDate.parse("2026-01-31");

        assertThat(RecurrenceCalendar.firstAfter(start, RecurrenceFrequency.MONTHLY, LocalDate.parse("2026-02-28")))
                .isEqualTo("2026-03-31");
    }
}
