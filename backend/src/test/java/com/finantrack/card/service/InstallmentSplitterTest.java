package com.finantrack.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstallmentSplitterTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void differenceGoesToFirstInstallment() {
        assertThat(InstallmentSplitter.split(money("100.00"), 3))
                .containsExactly(money("33.34"), money("33.33"), money("33.33"));
    }

    @Test
    void singleInstallmentIsTheTotal() {
        assertThat(InstallmentSplitter.split(money("59.90"), 1)).containsExactly(money("59.90"));
    }

    @Test
    void returnsEmptyWhenInstallmentWouldBeZero() {
        assertThat(InstallmentSplitter.split(money("0.02"), 3)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"100.00,3", "0.03,3", "1000.01,7", "9999.99,48", "0.48,48", "1234567.89,12"})
    void installmentsAlwaysSumExactlyToTotal(String total, int count) {
        List<BigDecimal> parts = InstallmentSplitter.split(money(total), count);

        assertThat(parts).hasSize(count).allMatch(part -> part.signum() > 0);
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
    }
}
