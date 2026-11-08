package com.finantrack.auth.dto;

import com.finantrack.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String name,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @ValidPassword
        String password) {

    /** Remove espaços das pontas antes da validação, comum ao colar o e-mail. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
