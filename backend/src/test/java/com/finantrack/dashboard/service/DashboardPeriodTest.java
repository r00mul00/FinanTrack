package com.finantrack.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finantrack.common.exception.InvalidParameterException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardPeriodTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-03-15");

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    @Test
    void defaultsToCurrentMonth() {
        DashboardPeriod period = DashboardPeriod.resolve(null, null, TODAY);

        assertThat(period.from()).isEqualTo(date("2026-03-01"));
        assertThat(period.to()).isEqualTo(date("2026-03-31"));
    }

    @Test
    void wholeMonthComparesWithPreviousWholeMonthEvenWithDifferentLength() {
        DashboardPeriod previous = new DashboardPeriod(date("2026-03-01"), date("2026-03-31")).previous();

        assertThat(previous.from()).isEqualTo(date("2026-02-01"));
        assertThat(previous.to()).isEqualTo(date("2026-02-28"));
    }

    @Test
    void januaryComparesWithDecemberOfPreviousYear() {
        DashboardPeriod previous = new DashboardPeriod(date("2026-01-01"), date("2026-01-31")).previous();

        assertThat(previous.from()).isEqualTo(date("2025-12-01"));
        assertThat(previous.to()).isEqualTo(date("2025-12-31"));
    }

    @Test
    void customPeriodComparesWithSameNumberOfDaysBefore() {
        DashboardPeriod previous = new DashboardPeriod(date("2026-03-11"), date("2026-03-20")).previous();

        assertThat(previous.from()).isEqualTo(date("2026-03-01"));
        assertThat(previous.to()).isEqualTo(date("2026-03-10"));
    }

    @Test
    void rejectsIncompleteInvertedOrTooLongPeriods() {
        assertThatThrownBy(() -> DashboardPeriod.resolve(date("2026-03-01"), null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> DashboardPeriod.resolve(date("2026-03-02"), date("2026-03-01"), TODAY))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> DashboardPeriod.resolve(date("2020-01-01"), date("2026-01-02"), TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }
}
