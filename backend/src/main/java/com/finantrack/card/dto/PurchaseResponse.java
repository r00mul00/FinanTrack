package com.finantrack.card.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        String description,
        BigDecimal totalAmount,
        int installments,
        UUID categoryId,
        LocalDate purchaseDate,
        boolean canceled) {
}
