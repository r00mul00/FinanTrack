package com.finantrack.account.dto;

import com.finantrack.account.entity.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        boolean archived) {
}
