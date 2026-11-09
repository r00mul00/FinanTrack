package com.finantrack.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank(message = "Informe o novo e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String newEmail,

        @NotBlank(message = "Informe a senha atual.")
        String currentPassword) {

    public EmailChangeRequest {
        newEmail = newEmail == null ? null : newEmail.trim();
    }
}
