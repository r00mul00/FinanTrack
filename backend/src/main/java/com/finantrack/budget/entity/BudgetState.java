package com.finantrack.budget.entity;

import java.math.BigDecimal;

public enum BudgetState {
    NORMAL,
    WARNING,
    EXCEEDED;

    private static final BigDecimal WARNING_PERCENT = new BigDecimal("80");
    private static final BigDecimal FULL_PERCENT = new BigDecimal("100");

    /** Compara por multiplicação, sem arredondar: 79,99% é normal e 100,01% excedeu. */
    public static BudgetState of(BigDecimal used, BigDecimal limit) {
        BigDecimal usedTimesHundred = used.multiply(FULL_PERCENT);
        if (usedTimesHundred.compareTo(limit.multiply(FULL_PERCENT)) > 0) {
            return EXCEEDED;
        }
        return usedTimesHundred.compareTo(limit.multiply(WARNING_PERCENT)) >= 0 ? WARNING : NORMAL;
    }
}
