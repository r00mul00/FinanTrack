package com.finantrack.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Em uma fatura, "amount" é o valor em aberto e "cardId" leva ao cartão; em uma conta, "cardId" é nulo. */
public record UpcomingDueResponse(Type type, UUID id, UUID cardId, String description, BigDecimal amount,
        LocalDate dueDate, boolean overdue) {

    public enum Type {
        BILL,
        INVOICE
    }
}
