package com.finantrack.transaction.entity;

import com.finantrack.category.entity.CategoryType;

public enum TransactionType {
    INCOME,
    EXPENSE;

    public boolean matches(CategoryType categoryType) {
        return name().equals(categoryType.name());
    }
}
