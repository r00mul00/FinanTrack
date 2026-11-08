package com.finantrack.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BudgetLimitRequest(
        @NotNull(message = "Informe o limite.")
        @DecimalMin(value = "0.01", message = "O limite deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal limitAmount) {
}
