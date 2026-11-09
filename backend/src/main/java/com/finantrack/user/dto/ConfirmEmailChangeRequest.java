package com.finantrack.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailChangeRequest(
        @NotBlank(message = "Token ausente.")
        @Size(max = 128)
        String token) {
}
