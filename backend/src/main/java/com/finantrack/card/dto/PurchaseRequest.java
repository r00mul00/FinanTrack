package com.finantrack.card.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseRequest(
        @NotBlank(message = "Informe a descrição.")
        @Size(max = 180, message = "A descrição deve ter no máximo 180 caracteres.")
        String description,

        @NotNull(message = "Informe o valor.")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal totalAmount,

        @NotNull(message = "Informe o número de parcelas.")
        @Min(value = 1, message = "O número de parcelas deve estar entre 1 e 48.")
        @Max(value = 48, message = "O número de parcelas deve estar entre 1 e 48.")
        Integer installments,

        @NotNull(message = "Selecione a categoria.")
        UUID categoryId,

        @NotNull(message = "Informe a data da compra.")
        LocalDate purchaseDate) {

    public PurchaseRequest {
        description = description == null ? null : description.trim();
    }
}
