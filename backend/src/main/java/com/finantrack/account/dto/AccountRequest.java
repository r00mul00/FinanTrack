package com.finantrack.account.dto;

import com.finantrack.account.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "Informe o nome da conta.")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres.")
        String name,

        @NotNull(message = "Selecione o tipo da conta.")
        AccountType type,

        @NotNull(message = "Informe o saldo inicial.")
        @DecimalMin(value = "0.00", message = "O saldo inicial não pode ser negativo.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal initialBalance,

        Boolean archived) {

    /** "archived" é opcional e vale false quando ausente. */
    public AccountRequest {
        name = name == null ? null : name.trim();
        archived = Boolean.TRUE.equals(archived);
    }
}
