package com.finantrack.budget.service;

import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.category.service.CategoryReferenceHandler;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BudgetCategoryReferences implements CategoryReferenceHandler {

    private final BudgetRepository repository;
    private final Clock clock;

    public BudgetCategoryReferences(BudgetRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public long countReferences(UUID categoryId) {
        return repository.countByCategoryId(categoryId);
    }

    /** Se o destino já tem orçamento no mês, mantém o dele e remove o da categoria excluída. */
    @Override
    public void reassign(UUID fromCategoryId, UUID toCategoryId) {
        repository.deleteConflicting(fromCategoryId, toCategoryId);
        repository.reassignCategory(fromCategoryId, toCategoryId, clock.instant());
    }
}
