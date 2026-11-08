package com.finantrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank(message = "Token ausente.")
        @Size(max = 128)
        String token) {
}
