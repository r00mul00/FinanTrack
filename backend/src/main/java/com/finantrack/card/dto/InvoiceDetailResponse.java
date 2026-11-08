package com.finantrack.card.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDetailResponse(InvoiceResponse invoice, List<Line> lines) {

    public record Line(UUID purchaseId, String description, int number, int installments, BigDecimal amount,
            LocalDate purchaseDate) {
    }
}
