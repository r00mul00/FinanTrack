package com.finantrack.card.service;

import com.finantrack.card.repository.CreditCardPurchaseRepository;
import com.finantrack.category.service.CategoryReferenceHandler;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PurchaseCategoryReferences implements CategoryReferenceHandler {

    private final CreditCardPurchaseRepository repository;
    private final Clock clock;

    public PurchaseCategoryReferences(CreditCardPurchaseRepository repository, Clock clock) {
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
