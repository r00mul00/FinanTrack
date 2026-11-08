package com.finantrack.card.dto;

import com.finantrack.card.entity.CardBrand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Por contrato, só aceita os quatro últimos dígitos; outros campos enviados são descartados. */
public record CardRequest(
        @NotBlank(message = "Informe o apelido do cartão.")
        @Size(max = 60, message = "O apelido deve ter no máximo 60 caracteres.")
        String nickname,

        @NotNull(message = "Selecione a bandeira.")
        CardBrand brand,

        @NotNull(message = "Informe os quatro últimos dígitos.")
        @Pattern(regexp = "^\\d{4}$", message = "Informe apenas os quatro últimos dígitos.")
        String lastFour,

        @NotNull(message = "Informe o limite.")
        @DecimalMin(value = "0.01", message = "O limite deve ser maior que zero.")
        @Digits(integer = 12, fraction = 2, message = "Informe um valor com até 12 dígitos e 2 casas decimais.")
        BigDecimal creditLimit,

        @NotNull(message = "Informe o dia de fechamento.")
        @Min(value = 1, message = "O dia deve estar entre 1 e 31.")
        @Max(value = 31, message = "O dia deve estar entre 1 e 31.")
        Integer closingDay,

        @NotNull(message = "Informe o dia de vencimento.")
        @Min(value = 1, message = "O dia deve estar entre 1 e 31.")
        @Max(value = 31, message = "O dia deve estar entre 1 e 31.")
        Integer dueDay,

        Boolean archived) {

    public CardRequest {
        nickname = nickname == null ? null : nickname.trim();
        archived = Boolean.TRUE.equals(archived);
    }
}
