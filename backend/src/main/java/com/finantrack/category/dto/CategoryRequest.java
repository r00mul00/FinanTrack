package com.finantrack.category.dto;

import com.finantrack.category.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
        String name,

        @NotNull(message = "Selecione o tipo da categoria.")
        CategoryType type,

        @NotBlank(message = "Selecione um ícone.")
        @Size(max = 60)
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "Ícone inválido.")
        String icon,

        @NotBlank(message = "Selecione uma cor.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida.")
        String color,

        Boolean archived) {

    /** "archived" é opcional e vale false quando ausente. */
    public CategoryRequest {
        name = name == null ? null : name.trim();
        archived = Boolean.TRUE.equals(archived);
    }
}
