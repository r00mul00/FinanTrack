package com.finantrack.category.service;

import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida a categoria escolhida pelo usuário em lançamentos, compras, orçamentos e recorrências. */
@Component
public class CategoryLookup {

    private final CategoryRepository categories;

    public CategoryLookup(CategoryRepository categories) {
        this.categories = categories;
    }

    /**
     * Categoria do usuário, do tipo esperado e não arquivada. Uma categoria
     * arquivada só é aceita se for a que o registro já utilizava ({@code currentId}).
     */
    public Category usable(UUID id, UUID userId, CategoryType expectedType, String typeMismatchMessage,
            UUID currentId) {
        Category category = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        if (category.getType() != expectedType) {
            throw new BusinessRuleException(typeMismatchMessage);
        }
        if (category.isArchived() && !Objects.equals(currentId, category.getId())) {
            throw new BusinessRuleException("A categoria selecionada está arquivada.");
        }
        return category;
    }
}
