package com.finantrack.transaction.entity;

import com.finantrack.category.entity.CategoryType;

public enum TransactionType {
    INCOME,
    EXPENSE;

    /** Tipo de categoria que um lançamento deste tipo deve usar. */
    public CategoryType categoryType() {
        return CategoryType.valueOf(name());
    }
}
