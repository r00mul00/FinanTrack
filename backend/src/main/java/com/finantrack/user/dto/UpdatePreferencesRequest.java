package com.finantrack.user.dto;

import com.finantrack.user.entity.Theme;
import jakarta.validation.constraints.Pattern;

/** Atualização parcial: campo ausente mantém o valor atual. */
public record UpdatePreferencesRequest(
        @Pattern(regexp = "BRL|USD|EUR|GBP", message = "Moeda não disponível.")
        String currency,
        Theme theme,
        Boolean notifyBudget,
        Boolean notifyDueDates) {
}
