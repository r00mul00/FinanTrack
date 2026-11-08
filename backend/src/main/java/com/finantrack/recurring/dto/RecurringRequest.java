package com.finantrack.recurring.dto;

import com.finantrack.recurring.entity.RecurrenceFrequency;
import com.finantrack.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringRequest(
        @NotBlank(message = "Informe a descrição.")
        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.")
        String description,

        @NotNull(message = "Informe o valor.")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal amount,

        @NotNull(message = "Selecione o tipo.")
        TransactionType type,

        @NotNull(message = "Selecione a categoria.")
        UUID categoryId,

        @NotNull(message = "Selecione a conta.")
        UUID accountId,

        @NotNull(message = "Selecione a frequência.")
        RecurrenceFrequency frequency,

        @NotNull(message = "Informe a data de início.")
        LocalDate startDate,

        LocalDate endDate,

        /* Na edição, a versão carregada na tela: detecta alteração feita em outro lugar. */
        Long version) {

    public RecurringRequest {
        description = description == null ? null : description.trim();
    }
}
