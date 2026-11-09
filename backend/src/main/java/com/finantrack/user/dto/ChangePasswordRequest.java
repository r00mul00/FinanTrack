package com.finantrack.user.dto;

import com.finantrack.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Informe a senha atual.")
        String currentPassword,

        @ValidPassword
        String newPassword) {
}
