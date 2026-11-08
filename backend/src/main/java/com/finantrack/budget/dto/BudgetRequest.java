package com.finantrack.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetRequest(
        @NotNull(message = "Selecione a categoria.")
        UUID categoryId,

        @NotNull(message = "Informe o mês.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Informe o mês no formato AAAA-MM.")
        String month,

        @NotNull(message = "Informe o limite.")
        @DecimalMin(value = "0.01", message = "O limite deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal limitAmount) {
}
