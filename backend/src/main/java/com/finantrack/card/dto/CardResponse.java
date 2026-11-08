package com.finantrack.card.dto;

import com.finantrack.card.entity.CardBrand;
import java.math.BigDecimal;
import java.util.UUID;

public record CardResponse(
        UUID id,
        String nickname,
        CardBrand brand,
        String lastFour,
        BigDecimal creditLimit,
        BigDecimal usedLimit,
        BigDecimal availableLimit,
        int closingDay,
        int dueDay,
        boolean archived) {
}
