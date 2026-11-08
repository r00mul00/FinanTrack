package com.finantrack.goal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GoalMovementRequest(
        @NotNull(message = "Informe o valor.")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal amount,

        @Size(max = 200, message = "A observação deve ter no máximo 200 caracteres.")
        String note) {

    public GoalMovementRequest {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
