package com.finantrack.recurring.service;

import com.finantrack.category.service.CategoryReferenceHandler;
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecurringCategoryReferences implements CategoryReferenceHandler {

    private final RecurringTransactionRepository repository;
    private final Clock clock;

    public RecurringCategoryReferences(RecurringTransactionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public long countReferences(UUID categoryId) {
        return repository.countByCategoryId(categoryId);
    }

    @Override
    public void reassign(UUID fromCategoryId, UUID toCategoryId) {
        repository.reassignCategory(fromCategoryId, toCategoryId, clock.instant());
    }
}
