package com.finantrack.goal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalRequest(
        @NotBlank(message = "Informe o nome da meta.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String description,

        @NotNull(message = "Informe o valor desejado.")
        @DecimalMin(value = "0.01", message = "O valor desejado deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal targetAmount,

        @NotNull(message = "Informe o valor inicial.")
        @DecimalMin(value = "0.00", message = "O valor inicial não pode ser negativo.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal initialAmount,

        @NotNull(message = "Informe o prazo.")
        LocalDate deadline,

        /* Na edição, a versão carregada na tela: detecta alteração feita em outro lugar. */
        Long version) {

    public GoalRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
