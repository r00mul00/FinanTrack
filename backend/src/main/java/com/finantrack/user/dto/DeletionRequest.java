package com.finantrack.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeletionRequest(
        @NotBlank(message = "Informe a senha atual.")
        String currentPassword) {
}
