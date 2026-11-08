package com.finantrack.auth.dto;

import com.finantrack.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token ausente.")
        @Size(max = 128)
        String token,

        @ValidPassword
        String newPassword) {
}
