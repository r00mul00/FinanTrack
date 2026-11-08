package com.finantrack.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryExpenseResponse(
        UUID categoryId,
        String name,
        String icon,
        String color,
        BigDecimal amount,
        BigDecimal percentage) {
}
