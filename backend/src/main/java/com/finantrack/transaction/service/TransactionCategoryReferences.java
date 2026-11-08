package com.finantrack.transaction.service;

import com.finantrack.category.service.CategoryReferenceHandler;
import com.finantrack.transaction.repository.TransactionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransactionCategoryReferences implements CategoryReferenceHandler {

    private final TransactionRepository repository;
    private final Clock clock;

    public TransactionCategoryReferences(TransactionRepository repository, Clock clock) {
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
