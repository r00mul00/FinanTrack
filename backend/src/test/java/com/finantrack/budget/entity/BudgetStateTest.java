package com.finantrack.budget.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BudgetStateTest {

    @ParameterizedTest
    @CsvSource({
            "0.00,    100.00, NORMAL",
            "79.99,   100.00, NORMAL",
            "80.00,   100.00, WARNING",
            "99.99,   100.00, WARNING",
            "100.00,  100.00, WARNING",
            "100.01,  100.00, EXCEEDED",
            "2.39,    3.00,   NORMAL",
            "2.40,    3.00,   WARNING",
            "3.01,    3.00,   EXCEEDED"
    })
    void classifiesAtExactBoundaries(String used, String limit, BudgetState expected) {
        assertThat(BudgetState.of(new BigDecimal(used), new BigDecimal(limit))).isEqualTo(expected);
    }
}
